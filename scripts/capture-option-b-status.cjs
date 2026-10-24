const {execFileSync}=require('node:child_process'),fs=require('node:fs'),path=require('node:path');
const kube=(...args)=>JSON.parse(execFileSync('kubectl',['--context','docker-desktop',...args,'-o','json'],{encoding:'utf8',stdio:['ignore','pipe','pipe']}));
(async()=>{
 const deployments=kube('get','deployments','-n','emerald-housing').items;
 const expected=['society-service','member-service','api-gateway','redis','redis-cache'];
 const workloads=expected.map(name=>{const d=deployments.find(d=>d.metadata.name===name);if(!d)throw Error('Missing deployment '+name);const desired=d.spec.replicas??1;const ready=d.status.readyReplicas??0;if(ready!==desired||d.status.updatedReplicas!==desired)throw Error('Deployment is not fully ready: '+name);return {name,desired,ready,image:d.spec.template.spec.containers[0].image};});
 const base=process.env.EMERALD_GATEWAY_URL||'http://localhost:8080',health=await fetch(base+'/actuator/health/readiness'),body=await health.json();if(!health.ok||body.status!=='UP')throw Error('Gateway readiness failed');
 const portal=await fetch(base+'/portal.html');if(portal.status!==200||!(await portal.text()).includes('Society portal'))throw Error('Portal check failed');
 const nodes=kube('get','nodes').items.map(n=>({name:n.metadata.name,kubernetesVersion:n.status.nodeInfo.kubeletVersion,runtime:n.status.nodeInfo.containerRuntimeVersion,ready:n.status.conditions.find(c=>c.type==='Ready')?.status==='True'}));
 if(nodes.some(n=>!n.ready))throw Error('Kubernetes node is not ready');
 const report={verifiedAt:new Date().toISOString(),context:'docker-desktop',namespace:'emerald-housing',nodes,workloads,gatewayReadiness:body.status,portalStatus:portal.status,baseUrl:base};
 const reportFile=path.resolve(__dirname,'../docs/verification/option-b/option-b-kubernetes-status.json');fs.mkdirSync(path.dirname(reportFile),{recursive:true});fs.writeFileSync(reportFile,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
})().catch(error=>{console.error(error.message);process.exitCode=1;});

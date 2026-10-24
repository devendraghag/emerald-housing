const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'..');
const newman=require(path.join(process.env.EMERALD_NEWMAN_HOME||path.join(os.tmpdir(),'emerald-postman-runner'),'node_modules/newman'));
const environment=JSON.parse(fs.readFileSync(path.join(root,'postman/Emerald-Housing-Option-B.postman_environment.json'),'utf8').replace(/^\uFEFF/,''));
const collection=JSON.parse(fs.readFileSync(path.join(root,'postman/Emerald-Housing-Option-B.postman_collection.json'),'utf8'));
const expectedRequests=collection.item.find(item=>item.name==='Option B - Automated verification').item.length;
let username=process.env.EMERALD_ADMIN_USERNAME,password=process.env.EMERALD_ADMIN_PASSWORD;
if(!username||!password){
 const secret=JSON.parse(execFileSync('kubectl',['--context','docker-desktop','get','secret','emerald-security-credentials','-n','emerald-housing','-o','json'],{encoding:'utf8',stdio:['ignore','pipe','pipe']}));
 username=Buffer.from(secret.data['admin-username'],'base64').toString();password=Buffer.from(secret.data['admin-password'],'base64').toString();
}
function set(key,value){const existing=environment.values.find(v=>v.key===key);if(existing)existing.value=value;else environment.values.push({key,value,enabled:true});}
set('adminUsername',username);set('adminPassword',password);set('externalBaseUrl',process.env.EMERALD_GATEWAY_URL||'http://localhost:8080');set('gatewayBaseUrl',process.env.EMERALD_GATEWAY_URL||'http://localhost:8080');set('runManualRequests','false');
if(process.argv.includes('--export-environment')){
 environment.name='Emerald Housing - Option B Localhost (private)';environment._postman_exported_at=new Date().toISOString();
 const privateFile=path.join(os.tmpdir(),'Emerald-Housing-Option-B-Private.postman_environment.json');
 fs.writeFileSync(privateFile,JSON.stringify(environment,null,2)+'\n',{mode:0o600});
 console.log('Private Postman environment exported outside the repository: '+privateFile);process.exit(0);
}
newman.run({collection,environment,folder:process.argv.includes('--all')?undefined:'Option B - Automated verification',delayRequest:200,timeoutRequest:30000,reporters:[]},(error,summary)=>{
 if(error){console.error('Newman runner failed: '+error.message);process.exitCode=1;return;}
 const redact=value=>String(value).replaceAll(password,'[REDACTED]').replaceAll(username,'[ADMIN]');
 const report={verifiedAt:new Date().toISOString(),selection:process.argv.includes('--all')?'Whole collection; manual reference disabled':'Automated verification folder',expectedRequests,baseUrl:process.env.EMERALD_GATEWAY_URL||'http://localhost:8080',stats:summary.run.stats,requests:summary.run.executions.map(e=>({name:e.item.name,method:e.request.method,status:e.response?.code,assertions:e.assertions?.map(a=>({name:a.assertion,passed:!a.error,error:a.error?redact(a.error.message):undefined}))})),failures:summary.run.failures.map(f=>({request:f.source?.name,error:redact(f.error?.message||f.error)}))};
 if(report.stats.requests.total!==expectedRequests)report.failures.push({request:'Runner completeness',error:`Expected ${expectedRequests} automated requests; sent ${report.stats.requests.total}`});
 const destination=path.join(root,'docs/verification/option-b/option-b-verification-summary.json');fs.mkdirSync(path.dirname(destination),{recursive:true});fs.writeFileSync(destination,JSON.stringify(report,null,2)+'\n');
 console.log(JSON.stringify({requests:report.stats.requests,assertions:report.stats.assertions,failures:report.failures,report:destination},null,2));
 // Raw Newman reports can contain passwords, session cookies and request bodies. Never export them.
 if(report.failures.length)process.exitCode=1;
});

const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),os=require('node:os');
const root=path.resolve(__dirname,'..'),collection=JSON.parse(fs.readFileSync(path.join(root,'postman/Emerald-Housing-Option-B.postman_collection.json'),'utf8'));
const expectedFiles=['Emerald-Housing-Option-B.postman_collection.json','Emerald-Housing-Option-B.postman_environment.json'].sort();
const actualFiles=fs.readdirSync(path.join(root,'postman')).sort();
if(JSON.stringify(actualFiles)!==JSON.stringify(expectedFiles))throw Error('postman must contain exactly the Option B collection and environment JSON files');
JSON.parse(fs.readFileSync(path.join(root,'postman/Emerald-Housing-Option-B.postman_environment.json'),'utf8'));
let scripts=0,requests=0;function walk(items){for(const item of items){for(const event of item.event||[]){new vm.Script(event.script.exec.join('\n'),{filename:item.name});scripts++;}if(item.request){requests++;if(item.request.body?.raw)JSON.parse(item.request.body.raw.replace(/\{\{[^}]+\}\}/g,'0'));}if(item.item)walk(item.item);}}
walk(collection.item);console.log(`Validated JSON bodies and ${scripts} scripts across ${requests} requests.`);
if(process.argv.includes('--gateway-smoke')){
 const newman=require(path.join(os.tmpdir(),'emerald-postman-runner/node_modules/newman'));
 collection.item=[{name:'Gateway smoke',item:collection.item[0].item.slice(0,5)}];
 const environment=JSON.parse(fs.readFileSync(path.join(root,'postman/Emerald-Housing-Option-B.postman_environment.json'),'utf8'));
 newman.run({collection,environment,delayRequest:200,reporters:[]},(error,summary)=>{
  if(error){console.error(error.message);process.exitCode=1;return;}
  const results={scope:'Gateway-only smoke; see the full verification summary for business-endpoint health',requests:summary.run.executions.map(e=>({name:e.item.name,status:e.response?.code})),assertions:summary.run.stats.assertions,failures:summary.run.failures.map(f=>({request:f.source?.name,message:f.error?.message}))};
  const reportFile=path.join(root,'docs/verification/option-b/option-b-gateway-smoke-summary.json');fs.mkdirSync(path.dirname(reportFile),{recursive:true});fs.writeFileSync(reportFile,JSON.stringify(results,null,2)+'\n');console.log(JSON.stringify(results,null,2));if(results.failures.length)process.exitCode=1;
 });
}

// DOM component tests; no browser, real accounts, or application databases are used.
const {JSDOM}=require(require('node:path').join(require('node:os').tmpdir(),'emerald-portal-dom-tests/node_modules/jsdom'));
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),base=path.join(root,'infrastructure/api-gateway/src/main/resources/static');
const html=fs.readFileSync(path.join(base,'portal.html'),'utf8'),source=fs.readFileSync(path.join(base,'portal.js'),'utf8');
const codes=[...new Set([...fs.readFileSync(path.join(root,'services/member-service/src/main/resources/db/migration/V6__dynamic_access_and_families.sql'),'utf8').matchAll(/\('([A-Z_]+)','[^']+'\)/g)].map(m=>m[1]).concat(['RENT_VIEW','RENT_MANAGE','MAINTENANCE_VIEW','MAINTENANCE_CREATE','MAINTENANCE_MANAGE','LEASE_VIEW','LEASE_MANAGE','VIEWING_VIEW','VIEWING_BOOK','VIEWING_MANAGE']))];
const ids={admin:'11111111-1111-4111-8111-111111111111',ownerAccount:'66666666-6666-4666-8666-666666666666',person:'22222222-2222-4222-8222-222222222222',flat:'33333333-3333-4333-8333-333333333333',ownership:'44444444-4444-4444-8444-444444444444',family:'55555555-5555-4555-8555-555555555555'};
const flush=async()=>{for(let i=0;i<10;i++)await new Promise(setImmediate);};
async function boot(role='ADMIN'){
 const dom=new JSDOM(html,{url:'http://portal.test/portal.html',runScripts:'outside-only'}),w=dom.window,writes=[];
 w.HTMLElement.prototype.scrollIntoView=function(){};w.confirm=()=>true;w.prompt=(_message,defaultValue)=>defaultValue||'Facilities team';
 const ownerGrants=['MEMBER_VIEW:SELF','FLAT_VIEW:OWN_FLATS','SOCIETY_VIEW:SOCIETY','RENT_VIEW:OWN_FLATS','RENT_MANAGE:OWN_FLATS','MAINTENANCE_VIEW:OWN_FLATS','MAINTENANCE_MANAGE:OWN_FLATS','LEASE_VIEW:OWN_FLATS','LEASE_MANAGE:OWN_FLATS','VIEWING_VIEW:OWN_FLATS','VIEWING_MANAGE:OWN_FLATS',...['VIEW','ADD','UPDATE','DEACTIVATE'].map(p=>'FAMILY_'+p+':OWN_FLATS')];
 const tenantGrants=['MEMBER_VIEW:SELF','FLAT_VIEW:OWN_FLATS','SOCIETY_VIEW:SOCIETY','RENT_VIEW:OWN_FLATS','MAINTENANCE_VIEW:OWN_FLATS','MAINTENANCE_CREATE:OWN_FLATS','LEASE_VIEW:OWN_FLATS','VIEWING_VIEW:OWN_FLATS','VIEWING_BOOK:OWN_FLATS'];
  const account={accountId:role==='ADMIN'?ids.admin:ids.ownerAccount,username:role==='ADMIN'?'administrator':role==='TENANT'?'tenant':'owner',roles:[role],role,societyCode:'EMERALD',memberId:role==='ADMIN'?null:ids.person,enabled:true,version:0};
 const person={memberId:ids.person,fullName:'Asha Shah',mobileNumber:'9876543210',email:null,status:'ACTIVE',version:0};
 const flats=[{flatId:ids.flat,flatNumber:'A-101',floorNumber:1,status:'ACTIVE'}];
 const owners=[{id:ids.ownership,member_id:ids.person,flat_id:ids.flat,full_name:'Asha Shah',start_date:'2026-01-01',end_date:null}];
 const families=[{id:ids.family,owner_membership_id:ids.ownership,flat_id:ids.flat,full_name:'Aarav Shah',relationship:'Child',mobile_number:null,email:null,linked_member_id:null,active:true,version:0}];
 const roles=[{code:'ADMIN',description:'Administrator',enabled:true,system_role:true,version:0,grants:codes.map(permission=>({permission,scope:'SOCIETY'}))},{code:'FLAT_OWNER',description:'Owner',enabled:true,system_role:false,version:0,grants:[{permission:'FAMILY_VIEW',scope:'OWN_FLATS'}]}];
 w.fetch=async(input,options={})=>{
  const u=new URL(input,w.location.href),method=options.method||'GET',body=options.body?JSON.parse(options.body):null;
  if(method!=='GET'){writes.push({path:u.pathname,method,body,headers:options.headers});if(u.pathname==='/api/v1/access/roles')roles.push({...body,system_role:false});return {ok:true,json:async()=>body||{}};}
  const fixture={
   '/api/v1/auth/csrf':{token:'fixture-csrf',headerName:'X-XSRF-TOKEN'},
   '/api/v1/accounts/me':{account,permissions:role==='ADMIN'?codes.map(p=>p+':SOCIETY'):role==='TENANT'?tenantGrants:ownerGrants,flatIds:[ids.flat]},
   '/api/v1/accounts':[account,{...account,accountId:ids.ownerAccount,username:'owner',roles:['FLAT_OWNER'],role:'FLAT_OWNER',memberId:ids.person}],
   '/api/v1/access/roles':roles,'/api/v1/access/permissions':codes.map(code=>({code,description:code})),
   '/api/v1/owners':owners,'/api/v1/families':families,'/api/v1/flats/page':{content:flats},'/api/v1/members/page':{content:[person]},
   '/api/v1/rental/payments':[{id:'payment-1',flatId:ids.flat,memberId:'',status:'OVERDUE',details:{period:'2026-11',amount:1250,dueDate:'2026-11-01'},version:0}],
   '/api/v1/rental/maintenance':[{id:'maintenance-1',flatId:ids.flat,memberId:ids.person,status:'OPEN',details:{summary:'Leaking tap',description:'Kitchen tap',priority:'HIGH'},version:0}],
   '/api/v1/rental/leases':[{id:'lease-1',flatId:ids.flat,memberId:'',status:'RENEWAL_OFFERED',details:{action:'RENEWAL',leaseEndDate:'2026-12-31'},version:0}],
   '/api/v1/rental/viewings':[{id:'viewing-1',flatId:ids.flat,memberId:'',status:'AVAILABLE',details:{slot:'2026-11-15T10:00:00Z'},version:0}],
   ['/api/v1/members/'+ids.person]:person,'/api/v1/members/audit':[],'/api/v1/society/audit':[]
  };
  assert.ok(u.pathname in fixture,'Unmocked request: '+u.pathname);return {ok:true,json:async()=>fixture[u.pathname]};
 };
 w.eval(source);await flush();
 const doc=w.document,section=title=>[...doc.querySelectorAll('section')].find(s=>s.querySelector('h2')?.textContent===title);
 const click=async(text,scope=doc)=>{const b=[...scope.querySelectorAll('button')].find(b=>b.textContent===text);assert.ok(b,'Missing button: '+text);b.click();await flush();};
 const submit=async(f)=>{f.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await flush();assert.notEqual(doc.querySelector('#notice').dataset.error,'true',doc.querySelector('#notice').textContent);};
 return {dom,w,doc,writes,section,click,submit};
}
async function adminScreensAndForms(){
 const t=await boot();try{
  assert.equal(t.doc.querySelector('#workspace').hidden,false);assert.equal(t.doc.querySelector('#notice').textContent,'');
  await t.click('Roles');let f=t.section('Role permissions').querySelector('form');f.elements.code.value='SECRETARY';f.elements.description.value='Society secretary';const grant=[...f.querySelectorAll('input[type=checkbox]')].find(i=>i.value==='MEMBER_VIEW');grant.checked=true;grant.closest('.grant-row').querySelector('select').value='SELF';await t.submit(f);
  assert.deepEqual(t.writes.at(-1).body.grants,[{permission:'MEMBER_VIEW',scope:'SELF'}]);assert.equal(t.writes.at(-1).headers['X-XSRF-TOKEN'],'fixture-csrf');
  await t.click('Users');f=t.section('Account details').querySelector('form');f.elements.username.value='new-user';f.elements.password.value='a-private-test-password';f.elements.roles.options[1].selected=true;await t.submit(f);assert.deepEqual(t.writes.at(-1).body.roles,['FLAT_OWNER']);
  await t.click('Owners & flats');f=t.section('Register a flat owner').querySelector('form');f.elements.fullName.value='New Owner';f.elements.mobileNumber.value='9876543211';f.elements.flatId.value=ids.flat;f.elements.username.value='new-owner';f.elements.password.value='another-test-password';await t.submit(f);assert.equal(t.writes.at(-1).path,'/api/v1/owners');assert.equal(t.writes.at(-1).body.flatId,ids.flat);
  await t.click('My family');await t.click('Edit',t.section('Family directory'));f=t.section('Family details').querySelector('form');f.elements.fullName.value='Changed Name';await t.submit(f);assert.equal(t.writes.at(-1).method,'PUT');assert.equal(t.writes.at(-1).body.details.fullName,'Changed Name');assert.equal(t.writes.at(-1).body.version,0);assert.equal('ownerMembershipId' in t.writes.at(-1).body.details,false);
  await t.click('Permissions');assert.ok(t.section('Permission catalogue'));await t.click('Audit history');assert.ok(t.section('Audit history'));
 }finally{t.dom.window.close();}
}
async function ownerScreensAndForms(){
 const t=await boot('FLAT_OWNER');try{
  assert.ok(t.section('My registered details'));assert.equal([...t.doc.querySelectorAll('nav button')].some(b=>b.textContent==='Users'),false);
  await t.click('My family');let f=t.section('Family details').querySelector('form');f.elements.fullName.value='Family Member';f.elements.relationship.value='Parent';await t.submit(f);assert.equal(t.writes.at(-1).body.ownerMembershipId,ids.ownership);assert.equal(t.writes.at(-1).body.details.linkedMemberId,null);
  await t.click('Deactivate',t.section('My family'));assert.equal(t.writes.at(-1).method,'PATCH');assert.equal(t.writes.at(-1).body.version,0);
  await t.click('Sign out');assert.equal(t.doc.querySelector('#workspace').hidden,true);assert.equal(t.doc.querySelector('#login').hidden,false);
 }finally{t.dom.window.close();}
}
async function tenantRentalScreens(){
 const t=await boot('TENANT');try{
  const tabs=[...t.doc.querySelectorAll('nav button')].map(b=>b.textContent);assert.ok(tabs.includes('Rent'));assert.ok(tabs.includes('Maintenance'));assert.ok(tabs.includes('Lease & move-out'));assert.ok(tabs.includes('Availability & viewings'));
  await t.click('Maintenance');let f=t.section('Create request').querySelector('form');f.elements.flatId.value=ids.flat;f.elements.summary.value='Broken heater';f.elements.description.value='The heater does not start';await t.submit(f);
  assert.equal(t.writes.at(-1).path,'/api/v1/rental/maintenance');assert.equal(t.writes.at(-1).body.flatId,ids.flat);assert.equal(t.writes.at(-1).body.summary,'Broken heater');
  assert.equal([...t.section('Maintenance requests').querySelectorAll('button')].some(b=>b.textContent==='Update status'),false);
  await t.click('Availability & viewings');await t.click('Book this slot',t.section('Availability and viewings'));assert.equal(t.writes.at(-1).path,'/api/v1/rental/viewings/viewing-1/book');assert.equal(t.writes.at(-1).method,'POST');
  await t.click('Lease & move-out');await t.click('Respond',t.section('Lease renewals and move-outs'));assert.equal(t.writes.at(-1).path,'/api/v1/rental/leases/lease-1');assert.equal(t.writes.at(-1).body.status,'RENEWAL_ACCEPTED');
 }finally{t.dom.window.close();}
}
(async()=>{await adminScreensAndForms();await ownerScreensAndForms();await tenantRentalScreens();process.stdout.write('Portal DOM tests passed: admin and owner screens, tenant rental requests/bookings/lease responses, account and family submissions, CSRF, and sign-out.\n');})().catch(e=>{console.error(e);process.exitCode=1;});

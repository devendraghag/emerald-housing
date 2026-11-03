const fs=require('node:fs');
const file='D:/Devendra Ghag Interview Preparation/02_Spring/emerald-housing-frontend/src/router/routes.cjs';
let text=fs.readFileSync(file,'utf8');
const before="{path:'/lost-found',redirect:'/notices'},";
const after="{path:'/lost-found',redirect:'/notices',meta:{requiresAuth:true,title:'Notices',permissions:['LOST_FOUND_BOARD:SOCIETY','LOST_FOUND_REPORT:SOCIETY','LOST_FOUND_ADMIN:SOCIETY']}},";
if(!text.includes(before))throw new Error('Expected compatibility redirect not found');
text=text.replace(before,after);
fs.writeFileSync(file,text,'utf8');
console.log('Added protected route metadata to the legacy redirect.');

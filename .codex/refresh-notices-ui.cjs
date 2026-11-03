const fs=require('node:fs');
const root='D:/Devendra Ghag Interview Preparation/02_Spring/emerald-housing-frontend/';
function change(file,before,after){let value=fs.readFileSync(root+file,'utf8');if(!value.includes(before))throw new Error('Missing expected UI text: '+before.slice(0,80));fs.writeFileSync(root+file,value.replace(before,after),'utf8');}
change('src/App.vue',
  '<summary class="button button-secondary button-small">Notifications',
  '<summary class="button button-secondary button-small" @click="loadNotifications">Notifications');
change('src/App.vue',
  'created(){window.addEventListener(\'emerald-session-expired\',this.sessionExpired);if(this.user)this.loadNotifications();},beforeUnmount(){window.removeEventListener(\'emerald-session-expired\',this.sessionExpired);},',
  'created(){window.addEventListener(\'emerald-session-expired\',this.sessionExpired);if(this.user)this.loadNotifications();this.notificationTimer=window.setInterval(()=>this.loadNotifications(),30000);},beforeUnmount(){window.clearInterval(this.notificationTimer);window.removeEventListener(\'emerald-session-expired\',this.sessionExpired);},');
change('src/views/Home.vue',
  '</router-link></div><p v-if="noticeLoading"',
  '</router-link></div><StatusMessage :message="noticeError" error/><p v-if="noticeLoading"');
console.log('Enabled notification refresh and overview notice error display.');

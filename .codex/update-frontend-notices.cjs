const fs = require('node:fs');
const path = require('node:path');

const root = 'D:/Devendra Ghag Interview Preparation/02_Spring/emerald-housing-frontend';
const changes = new Map();
function edit(file, replacements) {
  const target = path.join(root, file);
  let source = changes.has(target) ? changes.get(target) : fs.readFileSync(target, 'utf8');
  for (const [before, after] of replacements) {
    if (!source.includes(before)) throw new Error(`Expected text not found in ${file}: ${before.slice(0, 90)}`);
    source = source.replace(before, after);
  }
  changes.set(target, source);
}

edit('src/services/api.js', [[
  "  lostFoundNotifications:()=>http.get('/lost-found/notifications').then(data),",
  "  lostFoundNotifications:()=>http.get('/lost-found/notifications').then(data),\n  markLostFoundNotificationRead:eventId=>http.patch(`/lost-found/notifications/${eventId}/read`).then(data),\n  assignLostFoundFlat:(id,flatId)=>http.patch(`/lost-found/admin/reports/${id}/flat`,{flatId}).then(data),"
]]);

edit('src/App.vue', [
  ["import StatusMessage from './components/StatusMessage.vue';import Icon from './components/Icon.vue';import {mapState} from 'vuex';",
   "import StatusMessage from './components/StatusMessage.vue';import Icon from './components/Icon.vue';import {mapState} from 'vuex';import {api} from './services/api';"],
  ["<div class=\"topbar-actions\"><span class=\"admin-pill\" :title=\"rolesLabel\"><Icon name=\"shield\" :size=\"14\"/>{{ rolesLabel }}</span><button class=\"button button-secondary button-small\" :disabled=\"checkingSession\" @click=\"refreshAccess\">Refresh access</button>",
   "<div class=\"topbar-actions\"><span class=\"admin-pill\" :title=\"rolesLabel\"><Icon name=\"shield\" :size=\"14\"/>{{ rolesLabel }}</span><details v-if=\"can('LOST_FOUND_BOARD')\" class=\"notification-control\"><summary class=\"button button-secondary button-small\" aria-label=\"Notifications\">Notifications <span v-if=\"unreadNotificationCount\" class=\"notification-badge\">{{ unreadNotificationCount }}</span></summary><div class=\"notification-popover\"><p v-if=\"!notifications.length\" class=\"muted\">No notices yet.</p><button v-for=\"notice in notifications\" :key=\"notice.event_id||notice.eventId\" type=\"button\" class=\"notification-entry\" @click=\"openNotification(notice)\"><span>{{ notificationLabel(notice.event_type||notice.eventType) }}</span><small>{{ formatNotificationDate(notice.created_at||notice.createdAt) }}</small><b v-if=\"!(notice.read_at||notice.readAt)\">New</b></button></div></details><button class=\"button button-secondary button-small\" :disabled=\"checkingSession\" @click=\"refreshAccess\">Refresh access</button>"],
  "['/lost-found','Lost & Found','clock',['LOST_FOUND_BOARD','LOST_FOUND_REPORT','LOST_FOUND_ADMIN'].some(p=>this.can(p))]",
  "['/notices','Notices','clock',['LOST_FOUND_BOARD','LOST_FOUND_REPORT','LOST_FOUND_ADMIN'].some(p=>this.can(p))]"],
  ["computed:{\n  ...mapState('session',{user:'user',checkingSession:'checking',signingOut:'signingOut',sessionError:'error'}),",
   "computed:{\n  ...mapState('session',{user:'user',checkingSession:'checking',signingOut:'signingOut',sessionError:'error'}),\n  unreadNotificationCount(){return this.notifications.filter(n=>!(n.read_at||n.readAt)).length;},"],
  ["name:'EmeraldApp',mixins:[access],components:{StatusMessage,Icon},\n computed:{",
   "name:'EmeraldApp',mixins:[access],components:{StatusMessage,Icon},\n data(){return {notifications:[]};},\n computed:{"],
  ["created(){window.addEventListener('emerald-session-expired',this.sessionExpired);},beforeUnmount(){window.removeEventListener('emerald-session-expired',this.sessionExpired);},",
   "created(){window.addEventListener('emerald-session-expired',this.sessionExpired);if(this.user)this.loadNotifications();},beforeUnmount(){window.removeEventListener('emerald-session-expired',this.sessionExpired);},"],
  ["methods:{\n  focusMain(){",
   "methods:{\n  async loadNotifications(){if(!this.user||!this.can('LOST_FOUND_BOARD')){this.notifications=[];return;}try{this.notifications=await api.lostFoundNotifications();}catch{this.notifications=[];}},\n  notificationLabel(type){return ({LostFoundReportPublished:'A notice was published',LostFoundClaimSubmitted:'A claim was submitted',LostFoundReportResolved:'A report was resolved'})[type]||'Community notice';},\n  formatNotificationDate(value){if(!value)return '';const date=new Date(value);return Number.isNaN(date.getTime())?'':date.toLocaleString();},\n  async openNotification(notice){const eventId=notice.event_id||notice.eventId;const reportId=notice.report_id||notice.reportId;if(eventId){try{await api.markLostFoundNotificationRead(eventId);}catch{/* The notice can still be opened if marking read fails. */}}await this.loadNotifications();if(this.$route.path!=='/')await this.$router.push('/');this.$nextTick(()=>window.dispatchEvent(new CustomEvent('emerald-notice-focus',{detail:{reportId}})));},\n  focusMain(){"],
  ["},watch:{$route(){this.$store.commit('notice','');this.focusMain();}}",
   "},watch:{user(value){if(value)this.loadNotifications();else this.notifications=[];},$route(){this.$store.commit('notice','');this.focusMain();}}"]
]);

edit('src/router/routes.cjs', [[
  "{path:'/lost-found',component:LostFound,meta:{requiresAuth:true,title:'Lost & Found',permissions:['LOST_FOUND_BOARD:SOCIETY','LOST_FOUND_REPORT:SOCIETY','LOST_FOUND_ADMIN:SOCIETY']}},",
  "{path:'/notices',component:LostFound,meta:{requiresAuth:true,title:'Notices',permissions:['LOST_FOUND_BOARD:SOCIETY','LOST_FOUND_REPORT:SOCIETY','LOST_FOUND_ADMIN:SOCIETY']}},\n    {path:'/lost-found',redirect:'/notices'},"
]]);

edit('src/views/LostFound.vue', [
  ["<span class=\"page-kicker\">COMMUNITY</span>\n  <div class=\"page-heading\"><div><h1 class=\"page-title\">Lost &amp; Found</h1><p class=\"lead\">Share an item and help neighbours reconnect with what matters.</p></div>",
   "<span class=\"page-kicker\">FLAT COMMUNITY</span>\n  <div class=\"page-heading\"><div><h1 class=\"page-title\">Notices</h1><p class=\"lead\">Lost and found reports are visible only to members of the selected flat.</p></div>"],
  ["<section class=\"panel\"><div class=\"section-heading\"><div><h2 class=\"section-title\">In-app notifications</h2><p class=\"muted\">Updates about notices, claims, and resolved items.</p></div></div><p v-if=\"!notifications.length\" class=\"muted\">You are all caught up.</p><ul v-else class=\"notification-list\"><li v-for=\"n in notifications\" :key=\"n.event_id||n.eventId\"><strong>{{ eventLabel(n.event_type||n.eventType) }}</strong><small>{{ date(n.created_at||n.createdAt) }}</small><button class=\"link-button\" @click=\"focusNotice(n.report_id||n.reportId)\">View notice</button></li></ul></section>", ""],
  ["<form class=\"lost-found-form\" @submit.prevent=\"submitReport\">\n     <label class=\"field\">Report type",
   "<form class=\"lost-found-form\" @submit.prevent=\"submitReport\">\n     <label v-if=\"flatOptions.length!==1||can('LOST_FOUND_ADMIN')\" class=\"field field-wide\">Flat<select v-model=\"draft.flatId\" class=\"select-input\" required><option value=\"\" disabled>Select the flat this notice belongs to</option><option v-for=\"flat in flatOptions\" :key=\"flat.flatId\" :value=\"flat.flatId\">{{ flat.label }}</option></select></label>\n     <label class=\"field\">Report type"],
  ["<p class=\"muted\">Only approved reports appear here.</p>","<p class=\"muted\">Only approved notices for your active flat memberships appear here.</p>"],
  ["<div><strong>{{ r.notice.reportType }} · {{ r.notice.itemType==='OTHER'?r.notice.itemOther:r.notice.itemType }}</strong><span class=\"muted\">{{ r.notice.location }} · {{ date(r.notice.occurredAt) }}</span>",
   "<div><strong>{{ r.notice.reportType }} · {{ r.notice.itemType==='OTHER'?r.notice.itemOther:r.notice.itemType }}</strong><span class=\"muted\">{{ r.notice.location }} · {{ date(r.notice.occurredAt) }}</span><span class=\"muted\">Flat: {{ flatLabel(r.notice.flatId) }}</span>"],
  ["    <p>{{ r.notice.description }}</p><img v-if=\"r.notice.photoUrl\" class=\"notice-photo\" :src=\"r.notice.photoUrl\" alt=\"Report photo for admin review\">",
   "    <p>{{ r.notice.description }}</p><div v-if=\"!r.notice.flatId\" class=\"admin-actions\"><label class=\"field\">Assign legacy notice to flat<select v-model=\"flatAssignments[r.notice.id]\" class=\"select-input\"><option value=\"\" disabled>Select flat</option><option v-for=\"flat in flatOptions\" :key=\"flat.flatId\" :value=\"flat.flatId\">{{ flat.label }}</option></select></label><button class=\"button button-small\" :disabled=\"!flatAssignments[r.notice.id]\" @click=\"assignFlat(r)\">Assign flat</button></div><img v-if=\"r.notice.photoUrl\" class=\"notice-photo\" :src=\"r.notice.photoUrl\" alt=\"Report photo for admin review\">"],
  ["data(){return {draft:blank(),itemOptions:",
   "data(){return {draft:blank(),flatOptions:[],flatAssignments:{},itemOptions:"],
  ["const blank=()=>({reportType:'FOUND',itemType:'KEYS',itemOther:'',location:'',occurredAt:'',description:'',contactPreference:'SOCIETY_OFFICE',contactOther:''});",
   "const blank=()=>({flatId:'',reportType:'FOUND',itemType:'KEYS',itemOther:'',location:'',occurredAt:'',description:'',contactPreference:'SOCIETY_OFFICE',contactOther:''});"],
  ["mounted(){this.load();if(this.can('LOST_FOUND_ADMIN'))this.loadAdmin();},",
   "mounted(){this.load();this.loadFlatOptions();if(this.can('LOST_FOUND_ADMIN'))this.loadAdmin();},"],
  ["  async loadAdmin(){this.adminLoading=true;try{this.adminReports=await api.lostFoundAdminReports();}catch(e){this.error=errorMessage(e);}finally{this.adminLoading=false;}},",
   "  async loadFlatOptions(){try{const ids=(this.$store.state.session.user&&this.$store.state.session.user.flatIds)||[];let flats;if(this.can('LOST_FOUND_ADMIN')){const response=await api.page('flats',0,{size:100});flats=response.content||response.items||[];}else{flats=await Promise.all(ids.map(id=>api.flatLabel(id)));}this.flatOptions=flats.map(flat=>({flatId:flat.flatId||flat.id,label:flat.flatNumber||flat.number||flat.flatId||flat.id})).filter(flat=>flat.flatId);if(this.flatOptions.length===1)this.draft.flatId=this.flatOptions[0].flatId;}catch(e){this.error='Could not load your flats: '+errorMessage(e);}},\n  flatLabel(id){const flat=this.flatOptions.find(item=>item.flatId===id);return flat?flat.label:(id||'Not assigned');},\n  async loadAdmin(){this.adminLoading=true;try{this.adminReports=await api.lostFoundAdminReports();this.flatAssignments={...this.flatAssignments,...Object.fromEntries(this.adminReports.filter(r=>!r.notice.flatId).map(r=>[r.notice.id,'']))};}catch(e){this.error=errorMessage(e);}finally{this.adminLoading=false;}},\n  async assignFlat(report){const flatId=this.flatAssignments[report.notice.id];if(!flatId)return;try{await api.assignLostFoundFlat(report.notice.id,flatId);this.success='Notice assigned to the selected flat.';await Promise.all([this.load(),this.loadAdmin()]);}catch(e){this.error=errorMessage(e);}},"],
  ["eventLabel(t){return ({LostFoundReportPublished:'A notice was published',LostFoundClaimSubmitted:'A claim was submitted',LostFoundReportResolved:'A report was resolved'})[t]||'Lost & Found update';},\n  statusLabel(s){",
   "eventLabel(t){return ({LostFoundReportPublished:'A notice was published',LostFoundClaimSubmitted:'A claim was submitted',LostFoundReportResolved:'A report was resolved'})[t]||'Community notice';},\n  statusLabel(s){"],
  ["async showClaims(id){try{this.claimsByReport={...this.claimsByReport,[id]:await api.lostFoundClaims(id)};}catch(e){this.error=errorMessage(e);}},",
   "async showClaims(id){try{this.claimsByReport={...this.claimsByReport,[id]:await api.lostFoundClaims(id)};}catch(e){this.error=errorMessage(e);}},"],
  ["const blank=()=>({flatId:'',reportType:'FOUND',itemType:'KEYS',itemOther:'',location:'',occurredAt:'',description:'',contactPreference:'SOCIETY_OFFICE',contactOther:''});",
   "const blank=()=>({flatId:'',reportType:'FOUND',itemType:'KEYS',itemOther:'',location:'',occurredAt:'',description:'',contactPreference:'SOCIETY_OFFICE',contactOther:''});"]
]);

edit('src/views/Home.vue', [
  ["<section class=\"welcome-banner\">",
   "<section v-if=\"can('LOST_FOUND_BOARD')\" class=\"overview-notices panel\"><div class=\"section-heading\"><div><span class=\"page-kicker\">YOUR FLAT</span><h2 class=\"section-title\">Notice board</h2><p class=\"muted\">Published notices for flats where you are an active member.</p></div><router-link to=\"/notices\" class=\"button button-secondary button-small\">All notices</router-link></div><p v-if=\"noticeLoading\" class=\"muted\">Loading notices…</p><p v-else-if=\"!notices.length\" class=\"muted\">No published notices for your flats.</p><div v-else class=\"overview-notice-list\"><article v-for=\"notice in notices\" :id=\"'notice-'+notice.id\" :key=\"notice.id\" class=\"overview-notice-card\" tabindex=\"-1\"><div class=\"section-heading\"><strong>{{ notice.reportType==='FOUND'?'Found':'Lost' }} · {{ notice.itemType==='OTHER'?notice.itemOther:notice.itemType }}</strong><span class=\"status\">{{ notice.status }}</span></div><p>{{ notice.location }} · {{ formatNoticeDate(notice.occurredAt) }}</p><p class=\"notice-description\">{{ notice.description }}</p><img v-if=\"notice.photoUrl\" class=\"notice-photo\" :src=\"notice.photoUrl\" alt=\"Photo attached to notice\"><form v-if=\"can('LOST_FOUND_REPORT')&&['PUBLISHED','CLAIM_RECEIVED'].includes(notice.status)\" class=\"overview-claim-form\" @submit.prevent=\"claimNotice(notice)\"><label class=\"field\">Claim this item · include a private identifying detail<textarea v-model=\"claimText[notice.id]\" class=\"text-input\" maxlength=\"2000\" rows=\"2\" required></textarea></label><button class=\"button button-small\" :disabled=\"claimingNotice===notice.id\">{{ claimingNotice===notice.id?'Sending…':'Claim item' }}</button></form></article></div></section>\n  <section class=\"welcome-banner\">"],
  ["import access from '../util/access-mixin';import Pagination from '../components/Pagination.vue';",
   "import access from '../util/access-mixin';import Pagination from '../components/Pagination.vue';"],
  ["import access from '../util/access-mixin';import Pagination from '../components/Pagination.vue';import {pageItems,pageCount} from '../util/pagination.cjs';import Icon from '../components/Icon.vue';import StatusMessage from '../components/StatusMessage.vue';import {api,errorMessage} from '../services/api';",
   "import access from '../util/access-mixin';import Pagination from '../components/Pagination.vue';import {pageItems,pageCount} from '../util/pagination.cjs';import Icon from '../components/Icon.vue';import StatusMessage from '../components/StatusMessage.vue';import {api,errorMessage} from '../services/api';"],
  ["data:()=>({loading:false,error:'',memberTotal:null,flatTotal:null,refreshedAt:'',selectedFloor:null,floorFlats:[],floorLoading:false,floorError:'',floorRequest:0,floorPage:0,flatPage:0}),",
   "data:()=>({loading:false,error:'',memberTotal:null,flatTotal:null,refreshedAt:'',selectedFloor:null,floorFlats:[],floorLoading:false,floorError:'',floorRequest:0,floorPage:0,flatPage:0,notices:[],noticeLoading:false,noticeError:'',claimText:{},claimingNotice:null}),"],
  ["created(){this.load();},beforeUnmount(){if(this.floorController)this.floorController.abort();this.floorRequest++;},",
   "created(){this.load();if(this.can('LOST_FOUND_BOARD'))this.loadNotices();},mounted(){window.addEventListener('emerald-notice-focus',this.focusNoticeFromNotification);},beforeUnmount(){if(this.floorController)this.floorController.abort();this.floorRequest++;window.removeEventListener('emerald-notice-focus',this.focusNoticeFromNotification);},"],
  [" methods:{\n  async load(){",
   " methods:{\n  async loadNotices(){this.noticeLoading=true;this.noticeError='';try{this.notices=await api.lostFoundBoard();}catch(e){this.noticeError=errorMessage(e);}finally{this.noticeLoading=false;}},\n  formatNoticeDate(value){if(!value)return '';const date=new Date(value);return Number.isNaN(date.getTime())?'':date.toLocaleString();},\n  focusNoticeFromNotification(event){const id=event.detail&&event.detail.reportId;this.$nextTick(()=>{const el=document.getElementById('notice-'+id);if(el){el.scrollIntoView({behavior:'smooth',block:'center'});el.focus({preventScroll:true});}});},\n  async claimNotice(notice){this.claimingNotice=notice.id;try{await api.submitLostFoundClaim(notice.id,(this.claimText[notice.id]||'').trim());this.claimText={...this.claimText,[notice.id]:''};await this.loadNotices();this.$store.commit('notice','Your claim was sent to the society office.');}catch(e){this.noticeError=errorMessage(e);}finally{this.claimingNotice=null;}},\n  async load(){"]
]);

edit('src/App.vue', [[
  "</script>",
  "</script>\n<style scoped>\n.notification-control{position:relative}.notification-control>summary{list-style:none;cursor:pointer}.notification-control>summary::-webkit-details-marker{display:none}.notification-badge{display:inline-grid;place-items:center;min-width:19px;height:19px;margin-left:5px;border-radius:50%;background:#b34f43;color:#fff;font-size:11px}.notification-popover{position:absolute;z-index:20;right:0;top:calc(100% + 8px);width:min(340px,90vw);max-height:360px;overflow:auto;padding:10px;background:#fff;border:1px solid var(--line);border-radius:12px;box-shadow:0 12px 34px #1e35201f}.notification-entry{display:grid;width:100%;gap:4px;padding:10px;text-align:left;background:transparent;border:0;border-bottom:1px solid var(--line);cursor:pointer}.notification-entry:hover{background:#f4f7f4}.notification-entry small{color:var(--muted)}.notification-entry b{color:#176b50;font-size:11px}\n</style>"
]]);

edit('src/views/Home.vue', [[
  "</script>",
  "</script>\n<style scoped>\n.overview-notices{display:grid;gap:14px;margin:22px 0;padding:22px}.overview-notice-list{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:14px}.overview-notice-card{padding:16px;border:1px solid var(--line);border-radius:14px;background:#fff;scroll-margin:100px}.overview-notice-card:focus{outline:3px solid var(--focus-ring)}.overview-notice-card>p{color:var(--muted)}.overview-claim-form{display:grid;gap:10px;border-top:1px solid var(--line);padding-top:12px}.overview-claim-form .field{display:grid;gap:7px}\n@media(max-width:560px){.overview-notices{padding:16px}}\n</style>"
]]);

for (const [target, source] of changes) fs.writeFileSync(target, source, 'utf8');
console.log('Frontend notices update applied.');

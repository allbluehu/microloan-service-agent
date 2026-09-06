<script setup lang="ts">
import { ref, nextTick, computed } from 'vue';
// API 数据只用于显示；Vue 文本绑定统一转义客户和知识正文。
type Data = Record<string, any>;
const support = new URLSearchParams(location.search).get('view') === 'support';
const token = ref(sessionStorage.getItem('microloanAccessToken') || '');
const supportKey = ref(sessionStorage.getItem('microloanSupportKey') || '');
const customerNo = ref('C10001');
const password = ref('');
const stepPassword = ref('');
const stepDialog = ref<HTMLDialogElement>();
const sessionId = ref(`S-${crypto.randomUUID()}`);
const sessions = ref<Data[]>([]);
const messages = ref<Data[]>([]);
const loan = ref('L10001');
const application = ref('');
const product = ref('');
const region = ref('');
const date = ref(new Date().toISOString().slice(0,10));
const content = ref('我下个月要还多少钱？');
const busy = ref(false);
const status = ref('');
const error = ref('');
const summary = ref<string[]>([]);
const queue = ref<Data[]>([]);
const queueStatus = ref('OPEN');
const metrics = ref<Data[]>([]);
// 业务拒绝和澄清单列，不计为基础设施故障，避免误报工具可靠性。
const toolErrorRate = computed(() => {
  const count = (outcome: string) => metrics.value
    .filter(m => m.name === 'microloan.agent.tool.calls' && m.tags.includes('node=TOOL_EXECUTOR') && m.tags.includes(`outcome=${outcome}`))
    .reduce((sum, m) => sum + (m.measurements.find((v: Data) => v.statistic === 'COUNT')?.value || 0), 0);
  const failures = count('failure');
  const total = failures + count('success');
  return total ? `${(100 * failures / total).toFixed(2)}%（${failures}/${total}）` : '暂无已执行工具样本';
});
const reports = ref<Data[]>([]);
const report = ref<Data|null>(null);
const useStream = ref(true);
let retry: { session: string; body: Data } | null = null;
const titles: Record<string,string> = {NEXT_REPAYMENT_QUERY:'下一期账单', APPLICATION_STATUS_QUERY:'申请进度', PAYMENT_RECORDS_QUERY:'还款记录', EARLY_SETTLEMENT_QUOTE:'提前结清试算', HUMAN_HANDOFF:'人工服务', HIGH_RISK_COMPLAINT:'投诉处理', KNOWLEDGE_QUERY:'知识咨询', UNKNOWN:'待补充', CLARIFICATION_REQUIRED:'待澄清'};
const labels: Record<string,string> = {loanId:'贷款编号',dueDate:'还款日',principal:'本金',remainingPrincipal:'剩余本金',interest:'利息',fee:'费用',totalAmount:'合计金额',status:'状态',quoteId:'试算编号',validUntil:'有效期',notice:'说明',applicationId:'申请编号',ticketId:'工单编号',summary:'问题摘要',priority:'优先级',createdAt:'创建时间',conversationId:'会话',type:'类型',relatedLoanId:'关联贷款',missingMaterials:'待补材料',nextAction:'下一步',updatedAt:'更新时间',paymentId:'支付编号',paidAt:'支付时间',amount:'金额',bankCardLast4:'银行卡尾号',channel:'渠道'};
function show(value: any): string { return value == null ? '—' : typeof value === 'object' ? JSON.stringify(value) : String(value); }
async function api(path: string, options: RequestInit = {}, staff = support): Promise<any> {
  const response = await fetch(path,{...options,headers:{'Content-Type':'application/json',...(staff?{'X-Support-Key':supportKey.value}:{Authorization:`Bearer ${token.value}`}),...options.headers}});
  const body = await response.text();
  let data: any = {}; try { data = body ? JSON.parse(body) : {}; } catch { throw new Error('服务返回了无法解析的响应'); }
  if (!response.ok) { const e = new Error(data.message || `请求失败 ${response.status}`); Object.assign(e,{code:data.code}); throw e; }
  return data;
}
async function run(task: () => Promise<void>) { error.value=''; try { await task(); } catch(e) { error.value=(e as Error).message; } }
async function login() {
  await run(async()=>{ const result=await api('/api/v1/auth/login',{method:'POST',body:JSON.stringify({customerNo:customerNo.value,password:password.value})},false);
    token.value=result.accessToken; sessionStorage.setItem('microloanAccessToken',token.value); password.value=''; newSession(); await loadSessions(); status.value='登录成功'; });
}
function logout() { token.value=''; sessionStorage.removeItem('microloanAccessToken'); messages.value=[]; sessions.value=[]; retry=null; status.value='已退出'; }
async function stepUp() { await run(async()=>{const result=await api('/api/v1/auth/step-up',{method:'POST',body:JSON.stringify({password:stepPassword.value})}); token.value=result.accessToken; sessionStorage.setItem('microloanAccessToken',token.value); stepPassword.value=''; stepDialog.value?.close(); status.value='二次认证成功，5 分钟内有效，请重试消息';}); }
async function loadSessions() { sessions.value=await api('/api/v1/chat/sessions?limit=100'); }
function newSession() { sessionId.value=`S-${crypto.randomUUID()}`; messages.value=[]; summary.value=[]; retry=null; loan.value='';application.value='';product.value='';region.value=''; }
async function selectSession(id: string) { if(busy.value)return; await run(async()=>{const [history,ctx]=await Promise.all([api(`/api/v1/chat/sessions/${encodeURIComponent(id)}/messages?limit=100`),api(`/api/v1/chat/sessions/${encodeURIComponent(id)}/context`)]);sessionId.value=id;messages.value=history.reverse().map((m: Data)=>({...m,reason:m.feedbackReason||undefined,comment:m.feedbackComment||''}));loan.value=ctx.selectedLoanId||'';application.value=ctx.selectedApplicationId||'';product.value=ctx.selectedProductCode||'';region.value=ctx.selectedRegion||'';summary.value=ctx.recentIntents;retry=null;}); }
async function clearContext() { await run(async()=>{await api(`/api/v1/chat/sessions/${encodeURIComponent(sessionId.value)}/context`,{method:'DELETE'}); loan.value='';application.value='';product.value='';region.value='';summary.value=[];status.value='已清除业务选择，历史消息保留';}); }
async function trace(message: Data) { await run(async()=>{message.nodes=await api(`/api/v1/agent/traces/${encodeURIComponent(message.traceId)}`);}); }
async function feedback(message: Data, rating: string) { await run(async()=>{const result=await api(`/api/v1/chat/messages/${encodeURIComponent(message.messageId)}/feedback`,{method:'POST',body:JSON.stringify({rating,reason:rating==='DOWN'?(message.reason||'NOT_HELPFUL'):null,comment:message.comment||null})});message.feedbackRating=result.rating;status.value='评价已保存';}); }
async function stream(path: string, body: Data): Promise<Data> {
  const response=await fetch(path,{method:'POST',headers:{Authorization:`Bearer ${token.value}`,'Content-Type':'application/json'},body:JSON.stringify(body)});
  if(!response.ok) { const e=await response.json(); throw new Error(e.message||'连接失败'); }
  if(!response.body)throw new Error('浏览器不支持流式响应');
  const reader=response.body.getReader();const decoder=new TextDecoder();let buffer='';let result:Data|null=null;let doneEvent=false;
  try { while(true){const {value,done}=await reader.read();buffer+=done?decoder.decode():decoder.decode(value,{stream:true});buffer=buffer.replace(/\r\n/g,'\n');let end;
    while((end=buffer.indexOf('\n\n'))>=0){const frame=buffer.slice(0,end);buffer=buffer.slice(end+2);const lines=frame.split('\n');const event=lines.find(l=>l.startsWith('event:'))?.slice(6).trim();const data=lines.filter(l=>l.startsWith('data:')).map(l=>l.slice(5).trimStart()).join('\n');
      if(event==='status')status.value=data;
      if(event==='answer')result=JSON.parse(data);
      if(event==='done')doneEvent=true;
      if(event==='error'){const detail=JSON.parse(data);const e=new Error(detail.message);Object.assign(e,{code:detail.code});throw e;}
    }if(done)break;}
  } finally { await reader.cancel(); reader.releaseLock(); }
  // 断线不自动重发写动作；保留原 messageId，让客户明确重试且复用工单幂等键。
  if(!result||!doneEvent)throw new Error('连接中断，可使用相同消息重试'); return result;
}
async function send(isRetry=false) {
  if(busy.value||!token.value||(!isRetry&&!content.value.trim()))return;
  if(!isRetry){retry={session:sessionId.value,body:{messageId:`WEB-${crypto.randomUUID()}`,content:content.value.trim(),selectedLoanId:loan.value||null,selectedApplicationId:application.value||null,selectedProductCode:product.value||null,selectedRegion:region.value||null,settlementDate:date.value||null}};messages.value.push({role:'USER',content:content.value});}
  if(!retry)return;busy.value=true;error.value='';const current=retry;
  try {const base=`/api/v1/chat/sessions/${encodeURIComponent(current.session)}`;const result=useStream.value?await stream(`${base}/stream`,current.body):await api(`${base}/messages`,{method:'POST',body:JSON.stringify(current.body)});
    messages.value.push({role:'ASSISTANT',content:result.answer,...result,messageId:`${current.body.messageId}:assistant`});content.value='';retry=null;status.value='处理完成';await loadSessions();
  }catch(e){error.value=(e as Error).message;if((e as Data).code==='STEP_UP_AUTH_REQUIRED')stepDialog.value?.showModal();}
  finally{busy.value=false;await nextTick();document.querySelector('.messages')?.scrollTo({top:999999,behavior:'smooth'});}
}
function logoutSupport() { supportKey.value='';sessionStorage.removeItem('microloanSupportKey');queue.value=[];metrics.value=[]; }
async function loadSupport() { await run(async()=>{const [tickets,stats]=await Promise.all([api(`/api/v1/support/tickets?status=${queueStatus.value}`),api('/api/v1/support/metrics')]);queue.value=tickets;metrics.value=stats;sessionStorage.setItem('microloanSupportKey',supportKey.value);}); }
async function ticketContext(ticket: Data) { await run(async()=>{ticket.context=(await api(`/api/v1/support/tickets/${encodeURIComponent(ticket.ticketId)}/context`)).reverse();}); }
async function transition(ticket: Data) { await run(async()=>{await api(`/api/v1/support/tickets/${encodeURIComponent(ticket.ticketId)}/transition`,{method:'POST',body:JSON.stringify({expectedStatus:ticket.status,note:ticket.note})});await loadSupport();status.value='工单状态已更新';}); }
async function importReports(event: Event) {await run(async()=>{const files=(event.target as HTMLInputElement).files;if(!files)return;const parsed=[];for(const file of files){const data=JSON.parse(await file.text());parsed.push({name:file.name,data});}reports.value=parsed;report.value=parsed[0]||null;});}
if(token.value&&!support)run(loadSessions);
</script>

<template>
<div class="shell">
<aside class="panel">
<h1>微贷服务中心</h1><nav><a href="/chat.html">客户对话</a> · <a href="/support.html">坐席工作台</a> · <a href="/knowledge-admin.html">知识管理</a></nav>
<template v-if="!support">
<form @submit.prevent="login"><label>客户编号<input v-model="customerNo" autocomplete="username" :disabled="busy"></label><label>密码<input v-model="password" type="password" autocomplete="current-password" required :disabled="busy"></label><button :disabled="busy">登录</button> <button type="button" class="secondary" @click="logout" :disabled="busy">退出</button></form>
<p v-if="token"><button @click="stepDialog?.showModal()" :disabled="busy">二次认证</button></p>
<h2>历史会话</h2><button @click="newSession" :disabled="busy">新建会话</button> <button @click="run(loadSessions)" :disabled="busy||!token">刷新</button>
<button v-for="s in sessions" :key="s.sessionId" class="session" :disabled="busy" @click="selectSession(s.sessionId)">{{s.sessionId}}<br><small>{{s.messageCount}} 条 · {{new Date(s.updatedAt).toLocaleString()}}</small></button>
</template>
<template v-else><label>坐席密钥<input type="password" v-model="supportKey" autocomplete="off"></label><button @click="loadSupport">连接工作台</button><p><small>密钥仅保存在当前标签页</small></p><button class="secondary" @click="logoutSupport">退出坐席</button></template>
</aside>
<main class="panel">
<header><h1>{{support?'人工接管与运行概况':'客户对话'}}</h1><span role="status">{{status}}</span></header><p v-if="error" class="error" role="alert">{{error}}</p>
<template v-if="!support">
<details><summary>业务选择与会话摘要</summary><small>{{sessionId}}</small><div class="fields"><label>贷款编号<input v-model="loan" :disabled="busy" placeholder="L10001"></label><label>申请编号<input v-model="application" :disabled="busy" placeholder="A10001"></label><label>产品编码<input v-model="product" :disabled="busy" placeholder="MICRO_BUSINESS"></label><label>地区<input v-model="region" :disabled="busy" placeholder="CN-SH"></label><label>试算日期<input type="date" v-model="date" :disabled="busy"></label></div><p>近期主题：{{summary.map(i=>titles[i]||i).join('、')||'暂无'}}</p><button @click="clearContext" :disabled="busy||!token">清除已保存选择</button><p class="muted">恢复最近 100 条消息；账单、支付和工单事实始终重新查询。</p></details>
<div class="messages" aria-live="polite"><p v-if="!messages.length" class="muted">选择贷款后可以查询账单，也可以咨询申请材料或请求人工服务。</p>
<article v-for="(m,index) in messages" :key="m.messageId||index" class="message" :class="{user:m.role==='USER'}"><strong>{{m.role==='USER'?'我':'微贷助手'}}</strong><div>{{m.content}}</div>
<div v-if="m.businessCard" class="card"><h2>{{titles[m.intent]||'业务详情'}}</h2><div class="fields"><template v-for="(value,key) in m.businessCard" :key="key"><div v-if="!['sources','answer','records'].includes(String(key))" class="field"><small>{{labels[String(key)]||key}}</small><div>{{show(value)}}</div></div></template></div><div v-for="(record,i) in m.businessCard.records" :key="i" class="card"><div v-for="(v,k) in record" :key="k">{{labels[String(k)]||k}}：{{show(v)}}</div></div><details v-for="source in m.businessCard.sources" :key="source.chunkId"><summary>{{source.title}} · {{source.sectionPath}} · {{source.version}}</summary><p>{{source.content}}</p><small>文档 {{source.documentId}} · 切片 {{source.chunkId}}</small></details></div>
<template v-if="m.role==='ASSISTANT'"><small>{{titles[m.intent]||m.intent}} · {{m.feedbackRating==='UP'?'已点赞':m.feedbackRating==='DOWN'?'已反馈问题':''}}</small><div class="row"><button class="secondary" @click="feedback(m,'UP')">有帮助</button><button class="secondary" @click="feedback(m,'DOWN')">提交问题反馈</button></div><details><summary>反馈原因和说明</summary><select v-model="m.reason"><option :value="undefined">没有解决问题</option><option value="INCORRECT_ANSWER">信息错误</option><option value="OUTDATED_INFORMATION">信息过期</option><option value="OTHER">其他</option></select><input v-model="m.comment" maxlength="500" placeholder="补充说明（可选）"></details><details v-if="m.traceId" @toggle="($event.target as HTMLDetailsElement).open&&!m.nodes&&trace(m)"><summary>查看处理过程</summary><small>{{m.traceId}}</small><div v-for="node in m.nodes" :key="node.nodeSequence" class="node"><strong>{{node.nodeSequence}}. {{node.nodeName}}</strong> · {{node.status}} · {{node.latencyMs}} ms<div>{{node.outputSummary}}</div><small>{{node.modelVersion}} {{node.promptVersion}}</small></div></details></template>
</article></div>
<form @submit.prevent="send()"><label>您的问题<textarea v-model="content" :disabled="busy" @keydown.enter.exact.prevent="send()" placeholder="输入问题；Shift + Enter 换行"></textarea></label><div class="row"><button :disabled="busy||!token">{{busy?'正在处理…':'发送'}}</button><button v-if="retry" type="button" @click="send(true)" :disabled="busy">重试上一条</button><label><input type="checkbox" v-model="useStream" style="width:auto" :disabled="busy">显示处理进度</label></div></form>
</template>
<template v-else>
<h2>工单队列</h2><div class="row"><select style="width:auto" v-model="queueStatus" @change="loadSupport"><option value="OPEN">待接单</option><option value="IN_PROGRESS">处理中</option><option value="RESOLVED">已解决</option></select><button @click="loadSupport">刷新</button></div><p v-if="!queue.length" class="muted">暂无工单，或尚未连接工作台。</p><div class="queue"><article v-for="ticket in queue" :key="ticket.ticketId" class="card"><h2>{{ticket.ticketId}} · {{ticket.priority==='HIGH'?'高优先级':'普通'}}</h2><p>{{ticket.summary}}</p><small>{{ticket.type}} · {{ticket.status}} · {{ticket.createdAt}}</small><details @toggle="($event.target as HTMLDetailsElement).open&&ticketContext(ticket)"><summary>客户对话上下文（最近 100 条）</summary><div v-for="m in ticket.context" :key="m.messageId" class="message"><strong>{{m.role}}</strong> {{m.content}}</div></details><template v-if="ticket.status!=='RESOLVED'"><label>处理说明<textarea v-model="ticket.note" maxlength="500"></textarea></label><button :disabled="!ticket.note?.trim()" @click="transition(ticket)">{{ticket.status==='OPEN'?'接单':'标记已解决'}}</button></template></article></div>
<h2>当前实例指标</h2><p>工具故障率：{{toolErrorRate}}</p><p class="muted">重启后计数清零。Token 仅在供应商返回 usage 后出现，缺失不代表零成本。</p><div v-for="metric in metrics" :key="metric.name+metric.tags.join()" class="metric"><strong>{{metric.name}}</strong><small> {{metric.tags.join(' · ')}}</small><div v-for="m in metric.measurements" :key="m.statistic">{{m.statistic}}：{{Number(m.value).toFixed(3)}}</div></div>
<h2>离线评测看板</h2><p class="muted">选择 target/evaluation 中的版本化 JSON 报告，在本机解析，不上传文件。</p><input type="file" accept=".json" multiple @change="importReports"><select v-if="reports.length" v-model="report"><option v-for="r in reports" :key="r.name" :value="r">{{r.name}}</option></select><div v-if="report" class="card"><div v-for="(v,k) in report.data" :key="k"><template v-if="!Array.isArray(v)&&typeof v!=='object'"><strong>{{k}}</strong>：{{show(v)}}</template><details v-else><summary>{{k}} {{Array.isArray(v)?`(${v.length})`:''}}</summary><pre style="white-space:pre-wrap;overflow-wrap:anywhere">{{JSON.stringify(v,null,2)}}</pre></details></div></div>
</template>
</main>
<dialog ref="stepDialog"><form @submit.prevent="stepUp"><h2>二次身份确认</h2><p>提前结清试算需要再次验证登录密码。</p><input v-model="stepPassword" type="password" autocomplete="current-password" required><p v-if="error" class="error">{{error}}</p><p class="row"><button>确认身份</button><button type="button" class="secondary" @click="stepPassword='';stepDialog?.close()">取消</button></p></form></dialog>
</div>
</template>

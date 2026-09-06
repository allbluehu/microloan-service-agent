import { test, expect } from '@playwright/test';

test('customer SSE, cards, feedback, trace and history recovery', async ({ page }) => {
  let submitted: any;
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let body: any = {};
    if (path.endsWith('/login')) body = { accessToken: 'test', expiresIn: 3600 };
    else if (path.endsWith('/stream')) {
      submitted = route.request().postDataJSON();
      return route.fulfill({ contentType:'text/event-stream',body: 'event:status\ndata:核验中\n\nevent:answer\ndata:' + JSON.stringify({traceId:'trace-test',intent:'NEXT_REPAYMENT_QUERY',answer:'下期应还 100.00 元',businessCard:{loanId:'L10001',totalAmount:'100.00'}}) + '\n\nevent:done\ndata:trace-test\n\n' });
    } else if (path.endsWith('/feedback')) {
      expect(route.request().postDataJSON()).toMatchObject({rating:'DOWN',reason:'NOT_HELPFUL'});
      body = {rating:'DOWN'};
    } else if (path.includes('/traces/')) body = [{nodeSequence:1,nodeName:'RESULT_VERIFIER',status:'SUCCESS',latencyMs:4,outputSummary:'result=VALID'}];
    else if (path.endsWith('/sessions')) body = [{sessionId:'S-HISTORY',messageCount:2,updatedAt:'2026-09-06T00:00:00Z'}];
    else if (path.endsWith('/context')) body = {selectedLoanId:'L10001',recentIntents:['NEXT_REPAYMENT_QUERY']};
    else if (path.endsWith('/messages')) body = [{messageId:'old:assistant',role:'ASSISTANT',content:'历史回答',intent:'NEXT_REPAYMENT_QUERY'}];
    await route.fulfill({json:body});
  });
  await page.goto('/chat.html');
  await page.getByLabel('密码',{exact:true}).fill('Demo123!');
  await page.getByRole('button',{name:'登录',exact:true}).click();
  await page.getByText('业务选择与会话摘要').click();
  await page.getByLabel('贷款编号',{exact:true}).fill('L10001');
  await page.getByRole('button',{name:'发送',exact:true}).click();
  await expect(page.getByText('下期应还 100.00 元')).toBeVisible();
  expect(submitted.selectedLoanId).toBe('L10001');
  await expect(page.getByText('合计金额')).toBeVisible();
  await page.getByRole('button',{name:'提交问题反馈'}).click();
  await expect(page.getByText('评价已保存')).toBeVisible();
  await page.getByText('查看处理过程').click();
  await expect(page.getByText('result=VALID')).toBeVisible();
  await page.getByRole('button',{name:/S-HISTORY/}).click();
  await expect(page.getByText('历史回答')).toBeVisible();
  await expect(page.getByLabel('贷款编号',{exact:true})).toHaveValue('L10001');
});

test('support ticket context and guarded transition', async ({page}) => {
  let transitioned=false;
  await page.route('**/api/v1/support/**',async route=>{
    expect(route.request().headers()['x-support-key']).toBe('staff-test');
    const path=new URL(route.request().url()).pathname;
    let body:any=[];
    if(path.endsWith('/metrics'))body=[{name:'microloan.agent.tool.calls',tags:['node=TOOL_EXECUTOR','outcome=success'],measurements:[{statistic:'COUNT',value:3}]},{name:'microloan.agent.tool.calls',tags:['node=TOOL_EXECUTOR','outcome=failure'],measurements:[{statistic:'COUNT',value:1}]}];
    if(path.endsWith('/tickets'))body=transitioned?[]:[{ticketId:'T1',priority:'HIGH',status:'OPEN',summary:'需要人工协助'}];
    if(path.endsWith('/context'))body=[{messageId:'M1',role:'USER',content:'请处理投诉'}];
    if(path.endsWith('/transition')){expect(route.request().postDataJSON()).toEqual({expectedStatus:'OPEN',note:'已联系客户'});transitioned=true;body={};}
    await route.fulfill({json:body});
  });
  await page.goto('/support.html');
  await page.getByLabel('坐席密钥').fill('staff-test');
  await page.getByRole('button',{name:'连接工作台'}).click();
  await expect(page.getByText('需要人工协助')).toBeVisible();
  await expect(page.getByText('工具故障率：25.00%（1/4）')).toBeVisible();
  await page.getByText('客户对话上下文（最近 100 条）').click();
  await expect(page.getByText('请处理投诉')).toBeVisible();
  await expect(page.getByRole('button',{name:'接单',exact:true})).toBeDisabled();
  await page.getByLabel('处理说明').fill('已联系客户');
  await page.getByRole('button',{name:'接单',exact:true}).click();
  await expect(page.getByText('工单状态已更新')).toBeVisible();
});

test('stream disconnect retries with the same message identifier',async({page})=>{
  const ids:string[]=[];
  await page.route('**/api/v1/**',async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/stream')){ids.push(route.request().postDataJSON().messageId);return route.fulfill({contentType:'text/event-stream',body:'event:status\ndata:处理中\n\n'});}
    await route.fulfill({json:path.endsWith('/login')?{accessToken:'test'}:[]});
  });
  await page.goto('/chat.html');await page.getByLabel('密码',{exact:true}).fill('Demo123!');await page.getByRole('button',{name:'登录',exact:true}).click();
  await page.getByRole('button',{name:'发送',exact:true}).click();await expect(page.getByRole('alert')).toContainText('连接中断');
  await page.getByRole('button',{name:'重试上一条'}).click();await expect.poll(()=>ids.length).toBe(2);expect(ids[0]).toBe(ids[1]);
});

test('step-up keeps retry identity and replaces the bearer token', async({page})=>{
  const ids:string[]=[];
  await page.route('**/api/v1/**',async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/stream')){
      ids.push(route.request().postDataJSON().messageId);
      if(ids.length===1)return route.fulfill({contentType:'text/event-stream',body:'event:error\ndata:{"code":"STEP_UP_AUTH_REQUIRED","message":"请完成二次认证"}\n\n'});
      expect(route.request().headers().authorization).toBe('Bearer elevated');
      return route.fulfill({contentType:'text/event-stream',body:'event:answer\ndata:{"answer":"试算完成","intent":"EARLY_SETTLEMENT_QUOTE"}\n\nevent:done\ndata:done\n\n'});
    }
    await route.fulfill({json:path.endsWith('/login')?{accessToken:'ordinary'}:path.endsWith('/step-up')?{accessToken:'elevated'}:[]});
  });
  await page.goto('/chat.html');await page.getByLabel('密码',{exact:true}).fill('Demo123!');await page.getByRole('button',{name:'登录',exact:true}).click();
  await page.getByLabel('您的问题').fill('提前结清试算');await page.getByRole('button',{name:'发送',exact:true}).click();
  const dialog=page.getByRole('dialog');await expect(dialog).toBeVisible();await dialog.locator('input').fill('Demo123!');await dialog.getByRole('button',{name:'确认身份'}).click();
  await expect(dialog).not.toBeVisible();await page.getByRole('button',{name:'重试上一条'}).click();await expect(page.getByText('试算完成')).toBeVisible();expect(ids[0]).toBe(ids[1]);
});

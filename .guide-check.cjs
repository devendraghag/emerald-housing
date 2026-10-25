const fs=require('fs'),path=require('path'),assert=require('assert/strict'),crypto=require('crypto');
const {JSDOM,VirtualConsole}=require(path.join(process.env.TEMP,'emerald-portal-dom-tests/node_modules/jsdom'));
const hash=text=>crypto.createHash('sha256').update(text).digest('hex');
const html=fs.readFileSync('indexLatest.html','utf8'),coverage=JSON.parse(fs.readFileSync('.guide-coverage.json','utf8'));
const errors=[],consoleEvents=[];const virtualConsole=new VirtualConsole();virtualConsole.on('jsdomError',error=>errors.push(error.message));virtualConsole.on('error',error=>consoleEvents.push(String(error)));
const dom=new JSDOM(html,{url:'http://localhost/indexLatest.html',runScripts:'dangerously',pretendToBeVisual:true,virtualConsole,beforeParse(w){w.HTMLElement.prototype.scrollIntoView=function(){};w.matchMedia=()=>({matches:false});w.print=()=>{};w.innerWidth=1280;}});
const w=dom.window,d=w.document;
const all=s=>[...d.querySelectorAll(s)];const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function run(){
 const ids=all('[id]').map(n=>n.id),duplicates=ids.filter((id,i)=>ids.indexOf(id)!==i);
 const missingOriginalIds=coverage.originalIds.filter(id=>!d.getElementById(id));
 const brokenFragments=all('a[href^="#"]').map(a=>a.getAttribute('href').slice(1)).filter(id=>id&&!d.getElementById(decodeURIComponent(id)));
 const brokenFiles=all('a[href]').map(a=>a.getAttribute('href')).filter(h=>!h.startsWith('#')&&!/^(https?:|mailto:|tel:|data:)/i.test(h)).filter(h=>{const p=decodeURIComponent(h.split('#')[0]);return p&&!fs.existsSync(path.resolve(p))});
 const currentExamples=new Set(all('pre code').map(n=>hash(n.textContent))),missingExamples=coverage.uniqueOriginalExampleHashes.filter(h=>!currentExamples.has(h));
 const currentDiagrams=new Set(all('svg').map(n=>hash(n.outerHTML))),missingDiagrams=coverage.uniqueOriginalDiagramHashes.filter(h=>!currentDiagrams.has(h));
 console.log(JSON.stringify({chapters:all('main>.chapter:not(.sources)').length,referenceGroups:all('.reference-item').length,uniqueOriginalExamples:coverage.uniqueOriginalExampleHashes.length,diagrams:all('svg').length,duplicates,missingOriginalIds,brokenFragments:[...new Set(brokenFragments)],brokenFiles:[...new Set(brokenFiles)],missingExamples:missingExamples.length,missingDiagrams:missingDiagrams.length,runtimeErrors:errors},null,2));
 assert.equal(duplicates.length,0,'Duplicate IDs');assert.equal(missingOriginalIds.length,0,'Original anchors lost');assert.equal(brokenFragments.length,0,'Broken fragments');assert.equal(brokenFiles.length,0,'Missing linked local files');assert.equal(missingExamples.length,0,'Original example lost');assert.equal(missingDiagrams.length,0,'Original diagram lost');assert.equal(errors.length,0,'Initialization errors');
 assert.equal(all('h1').length,1);assert.equal(all('main').length,1);assert.equal(all('style').length,1);assert.equal(all('main>.chapter:not(.sources)').length,12);
 assert.equal(all('script[src],link[rel="stylesheet"],img').length,0,'Self-contained resource check');
 assert.ok(d.body.lastElementChild.tagName==='SCRIPT');assert.ok(d.querySelector('main').compareDocumentPosition(d.querySelector('.site-footer'))&w.Node.DOCUMENT_POSITION_FOLLOWING);
 // Search actual narrative, a moved scenario, a zero-result query, and keyboard controls.
 const input=d.getElementById('doc-search');
 for(const term of ['redis','sharding','permission','ETag','cache stampede']){input.value=term;input.dispatchEvent(new w.Event('input'));await delay(130);assert.ok(all('.search-result').length>0,'Search failed for '+term);for(const a of all('.search-result'))assert.ok(d.getElementById(a.hash.slice(1)))}
 input.value='zero-result-6549871';input.dispatchEvent(new w.Event('input'));await delay(130);assert.equal(all('.search-result').length,0);assert.ok(d.getElementById('doc-search-status').textContent.startsWith('No matching'));
 input.value='redis';input.dispatchEvent(new w.Event('input'));await delay(130);input.focus();input.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));assert.ok(d.activeElement.matches('.search-result'));d.activeElement.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowUp',bubbles:true}));assert.equal(d.activeElement,input);
 const theme=d.getElementById('theme');theme.click();assert.equal(d.documentElement.dataset.theme,'dark');theme.click();assert.equal(d.documentElement.dataset.theme,'light');
 const toc=d.getElementById('toc-toggle-all');toc.click();assert.ok(all('.toc-chapter').every(n=>n.open));toc.click();assert.ok(all('.toc-chapter').every(n=>!n.open));
 const chapterExpand=d.querySelector('.expand-chapter');chapterExpand.click();assert.ok([...chapterExpand.closest('.chapter-references').querySelectorAll('.reference-item')].every(n=>n.open));chapterExpand.click();assert.ok([...chapterExpand.closest('.chapter-references').querySelectorAll('.reference-item')].every(n=>!n.open));
 const target=d.getElementById('redis-cache-scenarios');const parents=[];for(let n=target;n;n=n.parentElement)if(n.tagName==='DETAILS'){n.open=false;parents.push(n)}
 const anchor=d.createElement('a');anchor.href='#redis-cache-scenarios';d.body.append(anchor);anchor.click();assert.ok(parents.every(n=>n.open),'Deep-link ancestors');anchor.remove();
 w.innerWidth=390;const menu=d.getElementById('menubtn');menu.click();assert.ok(d.getElementById('side').classList.contains('open'));assert.equal(menu.getAttribute('aria-expanded'),'true');d.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true}));assert.equal(menu.getAttribute('aria-expanded'),'false');
 d.body.focus();menu.focus();d.dispatchEvent(new w.KeyboardEvent('keydown',{key:'/',bubbles:true}));assert.equal(d.activeElement,input);assert.equal(menu.getAttribute('aria-expanded'),'true');
 const states=all('main details').map(n=>n.open);w.dispatchEvent(new w.Event('beforeprint'));assert.ok(all('main details').every(n=>n.open));w.dispatchEvent(new w.Event('afterprint'));assert.deepEqual(all('main details').map(n=>n.open),states,'Print restores disclosures');
 const enlarge=d.querySelector('.diagram-tools button');enlarge.click();assert.ok(d.querySelector('.diagram.expanded[role="dialog"][aria-modal="true"]'));d.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true}));assert.ok(!d.querySelector('.diagram.expanded'));assert.equal(d.activeElement,enlarge);
 assert.equal(all('pre code').length,all('pre>.copy').length,'Copy control coverage');
 let copied='';Object.defineProperty(w.navigator,'clipboard',{value:{writeText:async text=>{copied=text}},configurable:true});const copy=d.querySelector('pre>.copy');copy.click();await delay(10);assert.equal(copied,copy.parentElement.querySelector('code').textContent);assert.equal(copy.textContent,'Copied');
 // Exercise the preserved Kubernetes explorer, including reset and back.
 const reveal=d.querySelector('#click-flow [data-reveal]');assert.ok(reveal);reveal.click();assert.equal(d.getElementById(reveal.dataset.reveal).hidden,false);d.getElementById('explorerBack').click();assert.equal(d.getElementById(reveal.dataset.reveal).hidden,true);reveal.click();d.getElementById('explorerReset').click();assert.ok(['x-namespace','x-resources','x-pods','x-services'].every(id=>d.getElementById(id).hidden));
 assert.equal(errors.length,0,errors.join('\n'));assert.equal(consoleEvents.length,0,consoleEvents.join('\n'));
 console.log('PASS: full source coverage, unique IDs, internal/file links, initialization, search, keyboard controls, theme, chapter disclosures, fragment reveal, mobile controls, print restoration, diagrams, clipboard, and Kubernetes explorer.');
 dom.window.close();
}
run().catch(error=>{console.error(error);dom.window.close();process.exitCode=1});

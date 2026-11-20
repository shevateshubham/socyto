import { useMemo, useState } from "react";
import { ArrowLeft, ArrowRight, ArrowUpRight, BarChart3, Bell, BriefcaseBusiness, Building2, Check, CheckCheck, ChevronDown, ChevronRight, CircleHelp, ClipboardCheck, Clock3, CreditCard, FilePlus2, FolderKanban, Home, KeyRound, Layers3, LayoutDashboard, LockKeyhole, Menu, MoreHorizontal, PackageOpen, Pencil, Plus, Search, Settings2, ShieldCheck, SlidersHorizontal, Sparkles, Users, Wallet, X } from "lucide-react";
import { Button } from "../components/Button";
import { Input } from "../components/Input";
import { Badge } from "../components/Badge";
import styles from "./_index.module.css";

type Role = "platform" | "business" | "client";
type View = "overview" | "projects" | "clients" | "materials" | "firms" | "design-login";
type Project = { id: string; name: string; client: string; place: string; phase: string; due: string; tasks: Task[]; receipts: Receipt[]; published: number; publicationNote: string; publishedAt: string | null };
type Task = { id: string; name: string; effort: number; tracking: "checklist" | "quantity"; done: boolean; required: number; completed: number; unit: string };
type Receipt = { id: string; name: string; qty: number; unit: string; rate: number; date: string };
type Firm = { id: string; name: string; category: string; status: "active" | "pending" | "suspended"; projects: number; palette: string; plan: string };
type Dialog = "firm" | "project" | "client" | "task" | "receipt" | "publish" | "branding" | null;

const identity = (prefix: string) => prefix + "-" + Math.random().toString(36).slice(2,9);
const initialProjects: Project[] = [
  { id:"p1",name:"The Courtyard Residence",client:"Aarav & Meera",place:"Pune · Residential interior",phase:"Execution",due:"14 Dec 2026",published:64,publicationNote:"Kitchen installations completed. Ceiling finishing is progressing.",publishedAt:"18 Nov 2026",tasks:[
    {id:"t1",name:"Civil modifications",effort:3,tracking:"checklist",done:true,required:1,completed:1,unit:""},
    {id:"t2",name:"Modular kitchen installation",effort:3,tracking:"quantity",done:false,required:14,completed:10,unit:"units"},
    {id:"t3",name:"Ceiling and finishing",effort:2,tracking:"quantity",done:false,required:620,completed:325,unit:"sq ft"},
    {id:"t4",name:"Final styling",effort:1,tracking:"checklist",done:false,required:1,completed:0,unit:""}
  ],receipts:[{id:"r1",name:"Plywood · BWP grade",qty:16,unit:"sheets",rate:3350,date:"16 Nov 2026"},{id:"r2",name:"Hardware fittings",qty:1,unit:"lot",rate:24750,date:"17 Nov 2026"}]},
  {id:"p2",name:"Atelier 09 Workspace",client:"Atelier Studios",place:"Satara · Commercial",phase:"On site",due:"09 Jan 2027",published:38,publicationNote:"Partition framing has begun. Weekly site review completed.",publishedAt:"16 Nov 2026",tasks:[
    {id:"t5",name:"Layouts & approvals",effort:2,tracking:"checklist",done:true,required:1,completed:1,unit:""},
    {id:"t6",name:"Partition framing",effort:3,tracking:"quantity",done:false,required:180,completed:48,unit:"running ft"},
    {id:"t7",name:"Electrical first fix",effort:3,tracking:"checklist",done:false,required:1,completed:0,unit:""}
  ],receipts:[{id:"r3",name:"Steel framing",qty:85,unit:"kg",rate:114,date:"15 Nov 2026"}]},
  {id:"p3",name:"Olive Grove Villa",client:"Kavya Family",place:"Wai · Villa",phase:"Planning",due:"20 Feb 2027",published:12,publicationNote:"Initial layout concepts reviewed with the client.",publishedAt:"11 Nov 2026",tasks:[
    {id:"t8",name:"Initial planning",effort:2,tracking:"checklist",done:true,required:1,completed:1,unit:""},
    {id:"t9",name:"Material selection",effort:2,tracking:"checklist",done:false,required:1,completed:0,unit:""},
    {id:"t10",name:"Site execution",effort:3,tracking:"checklist",done:false,required:1,completed:0,unit:""}
  ],receipts:[]}
];
const initialFirms: Firm[] = [
  {id:"f1",name:"Socyto Studio",category:"Architecture & interiors",status:"active",projects:3,palette:"#2E4A2B",plan:"Professional"},
  {id:"f2",name:"Atelier North",category:"Interior design",status:"pending",projects:0,palette:"#6B7662",plan:"Starter"},
  {id:"f3",name:"Vardhan Buildworks",category:"Construction",status:"active",projects:2,palette:"#76694D",plan:"Professional"}
];
const initialClients = ["Aarav & Meera","Atelier Studios","Kavya Family"];
const formatMoney=(n:number)=>"₹"+n.toLocaleString("en-IN",{maximumFractionDigits:0});
const percent=(p:Project)=>Math.round(p.tasks.reduce((sum,t)=>sum+t.effort*(t.tracking==="checklist"?(t.done?1:0):Math.min(1,Math.max(0,t.completed/Math.max(1,t.required)))),0)/Math.max(1,p.tasks.reduce((sum,t)=>sum+t.effort,0))*100);
const money=(p:Project)=>p.receipts.reduce((sum,r)=>sum+r.qty*r.rate,0);
const roleLabels:Record<Role,string>={platform:"Super Admin",business:"Business Admin",client:"Client"};
const initials=(name:string)=>name.split(" ").slice(0,2).map(x=>x[0]).join("").toUpperCase();
const navigation:Record<Role,{key:View;label:string;Icon:any}[]>={
  platform:[{key:"overview",label:"Overview",Icon:LayoutDashboard},{key:"firms",label:"Businesses",Icon:Building2},{key:"design-login",label:"Sign-in design",Icon:KeyRound}],
  business:[{key:"overview",label:"Overview",Icon:LayoutDashboard},{key:"projects",label:"Projects",Icon:FolderKanban},{key:"clients",label:"Clients",Icon:Users},{key:"materials",label:"Materials",Icon:PackageOpen},{key:"design-login",label:"Sign-in design",Icon:KeyRound}],
  client:[{key:"overview",label:"Overview",Icon:LayoutDashboard},{key:"projects",label:"My projects",Icon:FolderKanban},{key:"design-login",label:"Sign-in design",Icon:KeyRound}]
};
function ProgressRing({value,size=72}:{value:number;size?:number}){return <div className={styles.ring} style={{width:size,height:size,background:"conic-gradient(var(--secondary) "+Math.round(value*3.6)+"deg, #E5E9DD 0)"}}><div className={styles.ringInner}><strong>{Math.round(value)}%</strong></div></div>}
function Label({children}:{children:React.ReactNode}){return <span className={styles.eyebrow}>{children}</span>}
function SectionHeading({title,kicker,action}:{title:string;kicker?:string;action?:React.ReactNode}){return <div className={styles.sectionHeading}><div>{kicker&&<Label>{kicker}</Label>}<h2>{title}</h2></div>{action}</div>}
function Panel({children,className=""}:{children:React.ReactNode;className?:string}){return <section className={styles.panel+" "+className}>{children}</section>}
function Empty({title,detail}:{title:string;detail:string}){return <div className={styles.empty}><Layers3 size={32}/><h3>{title}</h3><p>{detail}</p></div>}

export default function SocytoStudio() {
 const [role,setRole]=useState<Role>("business");
 const [view,setView]=useState<View>("overview");
 const [dialog,setDialog]=useState<Dialog>(null);
 const [projects,setProjects]=useState<Project[]>(initialProjects);
 const [firms,setFirms]=useState<Firm[]>(initialFirms);
 const [clients,setClients]=useState<string[]>(initialClients);
 const [selectedProject,setSelectedProject]=useState<string|null>(null);
 const [selectedFirm,setSelectedFirm]=useState("f1");
 const [projectTab,setProjectTab]=useState<"overview"|"requirements"|"materials"|"updates">("overview");
 const [search,setSearch]=useState("");
 const [toast,setToast]=useState("");
 const [draft,setDraft]=useState<Record<string,string>>({});
 const [sidebarOpen,setSidebarOpen]=useState(false);
 const showToast=(m:string)=>{setToast(m);};
 const project=projects.find(p=>p.id===selectedProject);
 const firm=firms.find(f=>f.id===selectedFirm)||firms[0];
 const isClient=role==="client";
 const displayedProjects=projects.filter(p=>!search||[p.name,p.client,p.place].join(" ").toLowerCase().includes(search.toLowerCase()));
 const displayedFirms=firms.filter(f=>!search||f.name.toLowerCase().includes(search.toLowerCase()));
 const receiptTotal=projects.reduce((sum,p)=>sum+money(p),0);
 const openDialog=(type:Dialog)=>{setDraft({});setDialog(type);};
 const updateProject=(id:string,fn:(p:Project)=>Project)=>setProjects(prev=>prev.map(p=>p.id===id?fn(p):p));
 const chooseRole=(next:Role)=>{setRole(next);setView("overview");setSelectedProject(null);setSearch("");setSidebarOpen(false);};
 const chooseView=(next:View)=>{setView(next);setSelectedProject(null);setSidebarOpen(false);setSearch("");};
 const goProject=(id:string)=>{setSelectedProject(id);setProjectTab("overview");setView("projects");setSidebarOpen(false);};
 const projectCards=(items:Project[])=>items.length?<div className={styles.projectGrid}>{items.map(p=><button className={styles.projectCard} onClick={()=>goProject(p.id)} key={p.id}>
   <div className={styles.projectCardImage}><span className={styles.archLines}><i/><i/><i/></span><span className={styles.projectCategory}>{p.phase}</span></div>
   <div className={styles.projectCardBody}><div className={styles.projectCardTitle}><div><h3>{p.name}</h3><p>{p.client}</p></div><ArrowUpRight size={18}/></div><div className={styles.projectFoot}><div><div className={styles.footLabel}>Published progress</div><div className={styles.miniTrack}><span style={{width:p.published+"%"}}/></div></div><strong>{p.published}%</strong></div></div>
 </button>)}</div>:<Empty title="No projects" detail="Projects appear here once created and assigned."/>;
 const submitDialog=()=>{
   const val=(key:string)=>String(draft[key]||"").trim();
   if(dialog==="firm"){
     if(!val("name")||!val("email")){showToast("Enter a firm name and verified admin email.");return;}
     const newId=identity("firm");setFirms(x=>[...x,{id:newId,name:val("name"),category:val("category")||"Interior design",status:"pending",projects:0,palette:"#2E4A2B",plan:"Starter"}]);showToast("Design preview: firm added as pending.");setSelectedFirm(newId);
   }
   if(dialog==="client"){if(!val("name")){showToast("Enter a client name.");return;}setClients(x=>[...x,val("name")]);showToast("Design preview: client added.");}
   if(dialog==="project"){if(!val("name")||!val("client")){showToast("Enter project and client.");return;}const p:Project={id:identity("project"),name:val("name"),client:val("client"),place:val("place")||"Location not set",phase:"Planning",due:"To be scheduled",tasks:[],receipts:[],published:0,publicationNote:"",publishedAt:null};setProjects(x=>[p,...x]);showToast("Design preview: project created.");goProject(p.id);}
   if(dialog==="task"&&project){if(!val("name")){showToast("Add a task name.");return;}const tracking=val("tracking")==="quantity"?"quantity":"checklist";const required=Math.max(1,Number(val("required")||1));const task:Task={id:identity("task"),name:val("name"),tracking,effort:Math.min(3,Math.max(1,Number(val("effort")||2))),done:false,required,completed:0,unit:val("unit")};updateProject(project.id,p=>({...p,tasks:[...p.tasks,task]}));showToast("Draft requirement added. Not published.");}
   if(dialog==="receipt"&&project){if(!val("name")||Number(val("qty"))<=0||Number(val("rate"))<0){showToast("Enter material, positive quantity and rate.");return;}const r:Receipt={id:identity("receipt"),name:val("name"),qty:Number(val("qty")),rate:Number(val("rate")),unit:val("unit")||"unit",date:new Date().toLocaleDateString("en-GB")};updateProject(project.id,p=>({...p,receipts:[r,...p.receipts]}));showToast("Design preview: material receipt recorded.");}
   if(dialog==="publish"&&project){const value=percent(project);updateProject(project.id,p=>({...p,published:value,publicationNote:val("note")||"Project progress updated.",publishedAt:"Just now · design preview"}));showToast("Published snapshot updated in design preview.");}
   if(dialog==="branding"&&firm){setFirms(x=>x.map(f=>f.id===firm.id?{...f,name:val("name")||f.name,palette:val("palette")||f.palette}:f));showToast("Design preview: firm branding updated.");}
   setDialog(null);
 };
 const input=(key:string,placeholder:string,kind:string="text")=><Input type={kind} placeholder={placeholder} value={draft[key]||""} onChange={e=>setDraft(d=>({...d,[key]:e.target.value}))} aria-label={placeholder}/>;
 const fields=()=>{
  if(dialog==="firm")return <>{input("name","Business or studio name")}{input("email","Administrator's verified email","email")}{input("category","Business category")}</>;
  if(dialog==="client")return <>{input("name","Client name")}{input("email","Email (optional)","email")}</>;
  if(dialog==="project")return <>{input("name","Project title")}<label className={styles.fieldLabel}>Assigned client</label><select className={styles.nativeSelect} value={draft.client||""} onChange={e=>setDraft(d=>({...d,client:e.target.value}))}><option value="">Select a client</option>{clients.map(c=><option key={c}>{c}</option>)}</select>{input("place","Location and project type")}</>;
  if(dialog==="task")return <>{input("name","Requirement description")}<label className={styles.fieldLabel}>Tracking</label><select className={styles.nativeSelect} value={draft.tracking||"checklist"} onChange={e=>setDraft(d=>({...d,tracking:e.target.value}))}><option value="checklist">Completed / not completed</option><option value="quantity">Quantity-based</option></select><label className={styles.fieldLabel}>Relative effort</label><select className={styles.nativeSelect} value={draft.effort||"2"} onChange={e=>setDraft(d=>({...d,effort:e.target.value}))}><option value="1">Small · 1</option><option value="2">Medium · 2</option><option value="3">Large · 3</option></select>{draft.tracking==="quantity"&&<>{input("required","Target quantity","number")}{input("unit","Unit · sq ft, nos., m")}</>}</>;
  if(dialog==="receipt")return <>{input("name","Material description")}{input("qty","Received quantity","number")}{input("unit","Unit · sheets, kg, sq ft")}{input("rate","Unit rate in ₹","number")}</>;
  if(dialog==="publish")return <><div className={styles.publishSummary}>Private draft: <strong>{project?percent(project):0}%</strong><span>Client currently sees {project?.published||0}%</span></div><textarea rows={4} className={styles.nativeTextarea} value={draft.note||""} onChange={e=>setDraft(d=>({...d,note:e.target.value}))} placeholder="Share a clear client-facing update…"/></>;
  if(dialog==="branding")return <>{input("name","Display name")}{input("palette","Primary color e.g. #2E4A2B")}</>;
  return null;
 };
 return <div className={styles.app}>
   <title>SOCYTO · UI/UX Design Studio</title>
   <meta name="description" content="SOCYTO architectural project management design studio — mobile UI exploration."/>
   <aside className={styles.sidebar+" "+(sidebarOpen?styles.open:"")}>
     <div className={styles.brand}><div className={styles.monogram}><span>S</span></div><div><strong>SOCYTO</strong><small>DESIGN · EXECUTE · DELIVER</small></div></div>
     <div className={styles.sideRole}><span className={styles.sideRoleIcon}><Layers3 size={19}/></span><div><small>DESIGNING FOR</small><strong>{roleLabels[role]}</strong></div><ChevronDown size={16}/></div>
     <div className={styles.navHeading}>WORKSPACE</div>
     <nav>{navigation[role].map(({key,label,Icon})=><button key={key} className={styles.navLink+" "+(view===key&&!selectedProject?styles.active:"")} onClick={()=>chooseView(key)}><Icon size={19}/><span>{label}</span>{view===key&&!selectedProject&&<span className={styles.navDot}/>}</button>)}</nav>
     <div className={styles.sideBottom}><div className={styles.sideHelp}><CircleHelp size={20}/><div><strong>Design review</strong><span>Interactive concept only</span></div></div><div className={styles.sideFine}>SOCYTO v1 · Visual specification</div></div>
   </aside>
   {sidebarOpen&&<button className={styles.scrim} aria-label="Close navigation" onClick={()=>setSidebarOpen(false)}/>}
   <div className={styles.main}>
     <header className={styles.topbar}>
       <button className={styles.mobileMenu} aria-label="Open navigation" onClick={()=>setSidebarOpen(true)}><Menu size={23}/></button>
       <div className={styles.breadcrumb}><span>SOCYTO</span><ChevronRight size={15}/><strong>{selectedProject?"Project details":view==="design-login"?"Sign-in experience":navigation[role].find(x=>x.key===view)?.label||"Overview"}</strong></div>
       <div className={styles.topActions}><span className={styles.previewLabel}><span/> DESIGN PREVIEW</span><span className={styles.avatar}>SS</span></div>
     </header>
     <div className={styles.previewNotice}><Sparkles size={14}/><span><strong>Interactive design study</strong> · Illustrative examples only. No live accounts, projects or Supabase data are accessed.</span></div>
     <div className={styles.roleSwitch}><span>View experience</span>{(["platform","business","client"] as Role[]).map(value=><button key={value} onClick={()=>chooseRole(value)} className={role===value?styles.roleActive:""}>{roleLabels[value]}</button>)}</div>
     <main className={styles.content}>
       {view==="design-login"?<div className={styles.loginStage}><div className={styles.loginEditorial}><Label>AN ELEGANT WAY TO WORK</Label><h1>Every detail.<br/><em>In its place.</em></h1><p>One considered home for your practice, projects and client communications.</p><div className={styles.loginMotif}>S</div></div><Panel className={styles.loginPanel}><div className={styles.loginMark}>S</div><Label>WELCOME TO SOCYTO</Label><h2>Make room for great work.</h2><p>Refined, secure access to your workspace.</p><Input placeholder="Email address" aria-label="Email address"/><Input placeholder="Password" type="password" aria-label="Password"/><Button onClick={()=>showToast("Design preview only. Real authentication stays in native Android.")}>Sign in <ArrowRight size={16}/></Button><button className={styles.subtleLink} onClick={()=>showToast("Signup is preview-only. Native application uses Supabase email verification.")}>Create an account</button></Panel></div>:
       selectedProject&&project?(<div>
         <button className={styles.back} onClick={()=>setSelectedProject(null)}><ArrowLeft size={16}/> All projects</button>
         <div className={styles.projectDetailHead}><div><Label>{isClient?"CLIENT PROJECT":"PROJECT WORKSPACE"}</Label><h1>{project.name}</h1><p>{project.client} <span>·</span> {project.place}</p></div><span className={styles.statePill}><span/>{project.phase}</span></div>
         <div className={styles.detailHero}><div><Label>{isClient?"YOUR LATEST PUBLISHED PROGRESS":"CURRENT PROJECT OVERVIEW"}</Label><h2>{isClient?"A clear view of what’s complete.":"Progress you can put into perspective."}</h2><p>{isClient?"Only updates explicitly shared with you are visible here.":"Work requirements remain private until you publish an update."}</p></div><ProgressRing size={115} value={isClient?project.published:percent(project)}/></div>
         {isClient?<><SectionHeading title="Latest update" kicker="PUBLISHED TO YOU"/><Panel><div className={styles.updateTop}><Badge variant="success">Published</Badge><span>{project.publishedAt||"No publication"}</span></div><h3>{project.published}% complete</h3><div className={styles.track}><span style={{width:project.published+"%"}}/></div><p>{project.publicationNote||"No published update is available yet."}</p></Panel><div className={styles.securityNote}><LockKeyhole size={18}/> This portal shows published project progress only. Internal tasks, expenses and drafts are private.</div></>:
         <>
         <div className={styles.detailTabs}>{(["overview","requirements","materials","updates"] as const).map(t=><button key={t} className={projectTab===t?styles.detailActive:""} onClick={()=>setProjectTab(t)}>{({overview:"Overview",requirements:"Requirements",materials:"Materials",updates:"Publish"})[t]}</button>)}</div>
         {projectTab==="overview"&&<><div className={styles.metricGrid}><Panel className={styles.metric}><Label>PRIVATE DRAFT</Label><strong>{percent(project)}%</strong><small>Effort-weighted progress</small></Panel><Panel className={styles.metric}><Label>PUBLISHED</Label><strong>{project.published}%</strong><small>Last client snapshot</small></Panel><Panel className={styles.metric}><Label>RECEIVED MATERIALS</Label><strong>{formatMoney(money(project))}</strong><small>Actual expenditure</small></Panel></div><SectionHeading title="Work requirements" kicker="TASKS IN PROGRESS" action={<Button variant="outline" size="sm" onClick={()=>openDialog("task")}><Plus size={16}/> Add task</Button>}/><div className={styles.taskList}>{project.tasks.map(t=><div key={t.id} className={styles.taskRow}><div className={styles.taskIcon}><ClipboardCheck size={17}/></div><div><strong>{t.name}</strong><span>{t.tracking==="checklist"?"Checklist":t.completed+" / "+t.required+" "+t.unit} · Effort {t.effort}</span></div><span className={styles.taskPercentage}>{t.tracking==="checklist"?(t.done?"Done":"Pending"):Math.round(t.completed/t.required*100)+"%"}</span></div>)}</div></>}
         {projectTab==="requirements"&&<><SectionHeading title="Your private draft" kicker="TRACK & UPDATE" action={<Button size="sm" onClick={()=>openDialog("task")}><Plus size={16}/> Requirement</Button>}/><p className={styles.helpText}>Relative effort and completed quantities determine overall progress. None of these draft changes appear to clients until published.</p>{project.tasks.length===0?<Empty title="No requirements yet" detail="Add checklist or quantity-based tasks to begin."/>:<div className={styles.taskList}>{project.tasks.map(t=><div key={t.id} className={styles.taskRow}><div className={styles.taskIcon}><ClipboardCheck size={18}/></div><div><strong>{t.name}</strong><span>{t.tracking==="checklist"?"Checklist":"Quantity"} · Effort {t.effort}</span></div>{t.tracking==="checklist"?<button className={t.done?styles.checked:styles.unchecked} onClick={()=>updateProject(project.id,p=>({...p,tasks:p.tasks.map(x=>x.id===t.id?{...x,done:!x.done}:x)}))}>{t.done?<Check size={16}/>:null}</button>:<div className={styles.quantityControl}><button onClick={()=>updateProject(project.id,p=>({...p,tasks:p.tasks.map(x=>x.id===t.id?{...x,completed:Math.max(0,x.completed-1)}:x)}))}>−</button><strong>{t.completed}/{t.required}</strong><button onClick={()=>updateProject(project.id,p=>({...p,tasks:p.tasks.map(x=>x.id===t.id?{...x,completed:Math.min(x.required,x.completed+1)}:x)}))}>+</button></div>}</div>)}</div>}<div className={styles.draftCallout}><strong>{percent(project)}% current draft</strong><span>Client still sees {project.published}% until published.</span><Button onClick={()=>openDialog("publish")} size="sm">Publish update <ArrowRight size={15}/></Button></div></>}
         {projectTab==="materials"&&<><SectionHeading title="Material ledger" kicker="PROJECT EXPENDITURE" action={<Button size="sm" onClick={()=>openDialog("receipt")}><Plus size={16}/> Receipt</Button>}/><Panel className={styles.materialHero}><Label>EXPENDITURE FROM RECEIVED GOODS</Label><h2>{formatMoney(money(project))}</h2><p>Calculated from {project.receipts.length} material receipts · no allocated capital or invented stock totals</p></Panel>{project.receipts.length===0?<Empty title="No receipts" detail="Save a received material to calculate expenditure."/>:<Panel><div className={styles.ledgerHead}><span>Item</span><span>Value</span></div>{project.receipts.map(r=><div className={styles.ledgerRow} key={r.id}><div><strong>{r.name}</strong><small>{r.qty} {r.unit} · {r.date}</small></div><strong>{formatMoney(r.qty*r.rate)}</strong></div>)}</Panel>}</>}
         {projectTab==="updates"&&<><SectionHeading title="Client publications" kicker="EXPLICIT SHARING" action={<Button size="sm" onClick={()=>openDialog("publish")}><CheckCheck size={16}/> Publish draft</Button>}/><Panel><div className={styles.updateTop}><Badge variant="success">Last shared snapshot</Badge><span>{project.publishedAt||"Not yet published"}</span></div><h3>{project.published}% complete</h3><div className={styles.track}><span style={{width:project.published+"%"}}/></div><p>{project.publicationNote||"No client-facing update has been published."}</p></Panel><div className={styles.securityNote}><ShieldCheck size={18}/> Publication is always explicit. Draft changes never appear in the client portal automatically.</div></>}
         </>
         }
       </div>):role==="platform"?(<>
        <div className={styles.pageHead}><div><Label>PLATFORM ADMINISTRATION</Label><h1>{view==="firms"?"Business directory":<>A clearer way to <em>lead.</em></>}</h1><p>Everything happening across your independent business workspaces.</p></div><Button onClick={()=>openDialog("firm")}><Plus size={18}/> New business</Button></div>
        {view==="overview"&&<><div className={styles.metricGrid}><Panel className={styles.metric}><Label>TOTAL BUSINESSES</Label><strong>{firms.length.toString().padStart(2,"0")}</strong><small>Tenant workspaces</small></Panel><Panel className={styles.metric}><Label>ACTIVE FIRMS</Label><strong>{firms.filter(f=>f.status==="active").length.toString().padStart(2,"0")}</strong><small>Currently enabled</small></Panel><Panel className={styles.metric}><Label>PENDING APPROVAL</Label><strong>{firms.filter(f=>f.status==="pending").length.toString().padStart(2,"0")}</strong><small>Awaiting activation</small></Panel></div><div className={styles.featureNote}><div><Label>CURATED WORKSPACES</Label><h3>Every firm has its own identity.</h3><p>Manage branding, access, and project visibility independently, without compromising the SOCYTO experience.</p></div><Building2 size={64}/></div></>}
        <SectionHeading title="Business workspaces" kicker="MANAGE TENANTS" action={<div className={styles.search}><Search size={16}/><Input placeholder="Search firms" value={search} onChange={e=>setSearch(e.target.value)}/></div>}/>
        <div className={styles.firmGrid}>{displayedFirms.map(f=><Panel key={f.id} className={styles.firmCard}><div className={styles.firmTop}><div className={styles.firmLogo} style={{background:f.palette}}>{initials(f.name)}</div><Badge variant={f.status==="active"?"success":f.status==="pending"?"warning":"outline"}>{f.status}</Badge></div><h3>{f.name}</h3><p>{f.category}</p><div className={styles.firmMeta}><span>{f.plan} plan</span><strong>{f.projects} sites</strong></div><div className={styles.firmActions}><Button variant="outline" size="sm" onClick={()=>{setSelectedFirm(f.id);openDialog("branding")}}><Pencil size={15}/> Brand</Button><Button variant="ghost" size="sm" onClick={()=>{setFirms(x=>x.map(y=>y.id===f.id?{...y,status:y.status==="active"?"suspended":"active"}:y));showToast("Design preview: business status updated.")}}>{f.status==="active"?"Suspend":"Activate"} <ChevronRight size={15}/></Button></div></Panel>)}</div>
       </>):role==="business"?(<>
        <div className={styles.pageHead}><div><Label>SOCYTO STUDIO · INTERIORS</Label><h1>{view==="clients"?"Client relationships":view==="materials"?"Material expenditure":view==="projects"?"Our projects":<>Good morning, <em>architect.</em></>}</h1><p>{view==="overview"?"A considered view of every site, milestone, and client commitment.":"Your practice, organized with clarity."}</p></div><Button onClick={()=>openDialog(view==="clients"?"client":view==="materials"?"receipt":"project")}><Plus size={18}/> {view==="clients"?"Add client":view==="materials"?"New receipt":"New project"}</Button></div>
        {view==="overview"&&<><div className={styles.welcomeCard}><div><Label>YOUR STUDIO AT A GLANCE</Label><h2>Thoughtful spaces.<br/>Clear progress.</h2><p>Make every project easier to manage, from the first brief to the final detail.</p><button onClick={()=>chooseView("projects")}>Explore projects <ArrowRight size={17}/></button></div><div className={styles.welcomeDeco}><span/><span/><span/></div></div><div className={styles.metricGrid}><Panel className={styles.metric}><Label>PROJECTS</Label><strong>{projects.length.toString().padStart(2,"0")}</strong><small>In your workspace</small></Panel><Panel className={styles.metric}><Label>CLIENTS</Label><strong>{clients.length.toString().padStart(2,"0")}</strong><small>Assigned relationships</small></Panel><Panel className={styles.metric}><Label>RECEIVED MATERIALS</Label><strong>{formatMoney(receiptTotal)}</strong><small>Total receipt expenditure</small></Panel></div></>}
        {(view==="overview"||view==="projects")&&<><SectionHeading title={view==="overview"?"Projects in focus":"Project portfolio"} kicker="YOUR PRACTICE" action={<div className={styles.search}><Search size={16}/><Input placeholder="Find a project" value={search} onChange={e=>setSearch(e.target.value)}/></div>}/>{projectCards(displayedProjects)}</>}
        {view==="clients"&&<><SectionHeading title="Client directory" kicker="YOUR RELATIONSHIPS"/><Panel className={styles.clientList}>{clients.map((c,i)=><div key={c} className={styles.clientRow}><span className={styles.clientAvatar}>{initials(c)}</span><div><strong>{c}</strong><small>{projects.filter(p=>p.client===c).length} projects</small></div><ChevronRight size={18}/></div>)}</Panel></>}
        {view==="materials"&&<><Panel className={styles.materialHero}><Label>RECEIVED GOODS EXPENDITURE</Label><h2>{formatMoney(receiptTotal)}</h2><p>Summed across projects, from actual received material records</p></Panel><SectionHeading title="Project breakdown" kicker="MATERIAL LEDGER"/>{projects.map(p=><Panel key={p.id} className={styles.materialLine}><div><strong>{p.name}</strong><small>{p.receipts.length} receipts</small></div><strong>{formatMoney(money(p))}</strong></Panel>)}</>}
       </>):(<>
        <div className={styles.pageHead}><div><Label>YOUR PROJECT PORTAL</Label><h1>{view==="projects"?"Your project spaces":<>Welcome to your <em>project.</em></>}</h1><p>A thoughtful, transparent view of the work that's been shared with you.</p></div><span className={styles.readOnly}><LockKeyhole size={16}/> Read-only</span></div>
        {view==="overview"&&<div className={styles.clientHero}><div><Label>DESIGNED FOR CLARITY</Label><h2>Every update,<br/><em>beautifully considered.</em></h2><p>Watch your project take shape with clear, published milestones.</p></div><div className={styles.clientHeroDetail}><span>PROJECT PROGRESS</span><ProgressRing size={110} value={projects[0]?.published||0}/></div></div>}
        <SectionHeading title="Your projects" kicker="SHARED WITH YOU"/>{projectCards(view==="overview"?projects.slice(0,2):projects)}
        <div className={styles.securityNote}><ShieldCheck size={19}/> This is a read-only design preview. Clients see only their own published progress in the production app.</div>
       </>)}
     </main>
     <footer className={styles.footer}>SOCYTO <span>•</span> UI/UX design specification <span>•</span> Existing Kotlin + Supabase production app remains unchanged</footer>
     <nav className={styles.mobileBottom}>{navigation[role].slice(0,4).map(({key,label,Icon})=><button key={key} onClick={()=>chooseView(key)} className={view===key?styles.mobileSelected:""}><Icon size={20}/><span>{label}</span></button>)}</nav>
   </div>
   {toast&&<div role="status" className={styles.toast}><Check size={17}/><span>{toast}</span><button onClick={()=>setToast("")} aria-label="Dismiss"><X size={15}/></button></div>}
   {dialog&&<div className={styles.modalOverlay} onMouseDown={e=>{if(e.target===e.currentTarget)setDialog(null)}}><div className={styles.modal} role="dialog" aria-modal="true" aria-label="SOCYTO design action"><div className={styles.modalHead}><div><Label>DESIGN PREVIEW</Label><h2>{({firm:"Register a business",client:"New client",project:"New site project",task:"New work requirement",receipt:"New material receipt",publish:"Publish client update",branding:"Edit business identity"})[dialog]}</h2></div><button onClick={()=>setDialog(null)} aria-label="Close"><X size={19}/></button></div><div className={styles.modalContent}>{fields()}</div><div className={styles.modalFoot}><Button variant="outline" onClick={()=>setDialog(null)}>Cancel</Button><Button onClick={submitDialog}>{dialog==="publish"?"Publish update":"Save changes"} <ArrowRight size={17}/></Button></div></div></div>}
 </div>;
}

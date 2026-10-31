package com.example.aihub.screens

import org.json.JSONObject

/** هوش مصنوعی فقط مشخصات کاراکتر را به‌صورت JSON می‌نویسد؛ ساخت سه‌بعدی با این قالب ثابت و بدون خطا انجام می‌شود. */
internal const val CHAR_CFG_SYSTEM =
    "You design cute 3D toy-style characters. Reply with ONLY one JSON object (no markdown) with these keys: " +
        "\"name\" (short Persian name for the character), " +
        "\"species\": one of human, robot, cat, dog, bear, bunny, alien, monster; " +
        "\"skin\": hex color #RRGGBB of skin or fur; \"body\": hex shirt/body color; \"pants\": hex pants color; " +
        "\"hair\": hex hair color; \"hairStyle\": one of none, short, long, spiky, cap, hat; " +
        "\"eyes\": hex pupil color; \"accessory\": one of none, glasses, antenna, wings, horns, crown, tail; " +
        "\"ears\": one of none, cat, bunny, bear, dog; \"bodyShape\": one of slim, normal, round; " +
        "\"bg1\" and \"bg2\": two hex colors for a pleasant gradient background. " +
        "Choose colors and options that match the user's description and style (animals use ears and fur color in skin; robots use species robot)."

internal fun charHtml(cfg: String): String {
    val safe = runCatching { JSONObject(cfg.trim()).toString() }.getOrDefault("{}").replace("<", "\\u003c")
    return CHAR_TEMPLATE.replace("__CFG__", safe)
}

private val CHAR_TEMPLATE = """<!DOCTYPE html>
<html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>html,body{margin:0;height:100%;overflow:hidden;touch-action:none;background:#8fd3f4}canvas{display:block;width:100vw;height:100vh}#t{position:fixed;top:12px;left:0;right:0;text-align:center;font:600 18px sans-serif;color:#fff;text-shadow:0 1px 4px rgba(0,0,0,.45);pointer-events:none}</style></head>
<body><div id="t"></div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
<script>
var C=__CFG__;
function col(v,d){return /^#[0-9a-fA-F]{6}${'$'}/.test(v||"")?v:d}
var cfg={sp:C.species||"human",skin:col(C.skin,"#f2c9a0"),body:col(C.body,"#4f7cff"),pants:col(C.pants,"#2d3a6b"),hair:col(C.hair,"#3b2a1a"),hs:C.hairStyle||"short",eyes:col(C.eyes,"#3a2a1a"),acc:C.accessory||"none",ears:C.ears||"none",shape:C.bodyShape||"normal",bg1:col(C.bg1,"#8fd3f4"),bg2:col(C.bg2,"#84fab0"),name:C.name||""};
document.getElementById("t").textContent=cfg.name;
var scene=new THREE.Scene();
try{var gc=document.createElement("canvas");gc.width=2;gc.height=256;var gx=gc.getContext("2d");var gr=gx.createLinearGradient(0,0,0,256);gr.addColorStop(0,cfg.bg1);gr.addColorStop(1,cfg.bg2);gx.fillStyle=gr;gx.fillRect(0,0,2,256);scene.background=new THREE.CanvasTexture(gc)}catch(e){scene.background=new THREE.Color(cfg.bg1)}
var cam=new THREE.PerspectiveCamera(36,(innerWidth||1)/(innerHeight||1),0.1,100);var lw=0,lh=0;
var ren;
try{ren=new THREE.WebGLRenderer({antialias:true,alpha:false,preserveDrawingBuffer:true})}catch(e){ren=new THREE.WebGLRenderer({antialias:false,alpha:false,preserveDrawingBuffer:true})}
ren.setPixelRatio(Math.min(window.devicePixelRatio||1,2));ren.setSize(innerWidth,innerHeight);document.body.appendChild(ren.domElement);
scene.add(new THREE.HemisphereLight(0xffffff,0x8a97b0,0.75));
var key=new THREE.DirectionalLight(0xfff4e5,0.85);key.position.set(3,6,5);scene.add(key);
var rim=new THREE.DirectionalLight(0xbcd7ff,0.5);rim.position.set(-4,3,-4);scene.add(rim);
function M(c,r){return new THREE.MeshStandardMaterial({color:c,roughness:r||0.5,metalness:0.04})}
function mesh(g,c,x,y,z,r){var m=new THREE.Mesh(g,M(c,r));m.position.set(x||0,y||0,z||0);return m}
function sph(r,c,x,y,z,sx,sy,sz){var m=mesh(new THREE.SphereGeometry(r,32,22),c,x,y,z);m.scale.set(sx||1,sy||1,sz||1);return m}
function shade(hex,f){var c=new THREE.Color(hex);c.multiplyScalar(f);return "#"+c.getHexString()}
var S=cfg.shape==="round"?1.2:(cfg.shape==="slim"?0.88:1);
var robot=cfg.sp==="robot",animal=(cfg.sp==="cat"||cfg.sp==="dog"||cfg.sp==="bear"||cfg.sp==="bunny");
var skin=robot?"#cfd8e3":(cfg.sp==="alien"?"#9be37a":(cfg.sp==="monster"?"#8e6bd9":cfg.skin));
var G=new THREE.Group();scene.add(G);
var shadow=new THREE.Mesh(new THREE.CircleGeometry(1.0,40),new THREE.MeshBasicMaterial({color:0x000000,transparent:true,opacity:0.22}));
shadow.rotation.x=-Math.PI/2;shadow.position.y=-0.03;scene.add(shadow);
function lathe(pts,c,sx,sz){var v=pts.map(function(p){return new THREE.Vector2(p[0],p[1])});var m=mesh(new THREE.LatheGeometry(v,40),c,0,0,0);m.scale.set(sx,1,sz);return m}
// pelvis + torso
G.add(lathe([[0,0.95],[0.3,0.97],[0.42,1.05],[0.47,1.22],[0.46,1.4],[0,1.4]],cfg.pants,S,0.72));
var torso=lathe([[0,1.3],[0.44,1.32],[0.47,1.5],[0.44,1.8],[0.5,2.02],[0.46,2.14],[0.25,2.22],[0.13,2.3],[0,2.3]],cfg.body,S,0.72);G.add(torso);
var belt=mesh(new THREE.CylinderGeometry(0.455,0.455,0.07,40),"#2a2a30",0,1.36,0);belt.scale.set(S,1,0.72);G.add(belt);
G.add(mesh(new THREE.CylinderGeometry(0.13,0.15,0.2,20),skin,0,2.3,0));
// limbs
function limb(r1,r2,len,c){var g=new THREE.Group();g.add(mesh(new THREE.CylinderGeometry(r1,r2,len,22),c,0,-len/2,0));g.add(sph(r1,c,0,0,0));g.add(sph(r2,c,0,-len,0));return g}
function arm(side){
 var sh=new THREE.Group();sh.position.set(side*0.58*S,2.0,0);
 sh.add(limb(0.12,0.105,0.55,cfg.body));
 var el=new THREE.Group();el.position.set(0,-0.55,0);
 el.add(limb(0.1,0.085,0.5,robot?cfg.body:skin));
 var hand=new THREE.Group();hand.position.set(0,-0.55,0);
 hand.add(sph(0.11,skin,0,0,0,1,1.1,0.8));hand.add(sph(0.045,skin,side*0.1,0.0,0.05));
 el.add(hand);sh.add(el);sh.userData.el=el;return sh}
var armL=arm(-1),armR=arm(1);G.add(armL,armR);
function leg(side){
 var h=new THREE.Group();h.position.set(side*0.2*S,1.18,0);
 h.add(limb(0.17,0.14,0.55,cfg.pants));
 var kn=new THREE.Group();kn.position.set(0,-0.55,0);
 kn.add(limb(0.14,0.115,0.5,cfg.pants));
 var shoe=sph(0.17,"#2b2b33",0,-0.56,0.08,1,0.6,1.55);kn.add(shoe);
 kn.add(mesh(new THREE.CylinderGeometry(0.12,0.12,0.04,18),"#eeeeee",0,-0.5,0));
 h.add(kn);return h}
var legL=leg(-1),legR=leg(1);G.add(legL,legR);
// head
var head=new THREE.Group();head.position.set(0,2.72,0);G.add(head);
var skull=sph(0.56,skin,0,0,0,1,1.04,1);
if(cfg.sp==="alien")skull.scale.set(1.12,1.22,1);
if(robot)skull.scale.set(1.04,0.96,1.02);
head.add(skull);
function eye(x){var g=new THREE.Group();g.position.set(x,0.04,0.47);
 g.add(sph(0.125,"#ffffff",0,0,0,1,1.05,0.8));
 g.add(sph(0.075,cfg.eyes,0,0,0.06));g.add(sph(0.038,"#0b0b0b",0,0,0.105));
 g.add(sph(0.022,"#ffffff",0.03,0.035,0.125));return g}
var eyeL=eye(-0.21),eyeR=eye(0.21);head.add(eyeL,eyeR);
[-1,1].forEach(function(s){var b=mesh(new THREE.CylinderGeometry(0.018,0.018,0.19,8),(animal||robot)?shade(cfg.skin,0.6):shade(cfg.hair,0.9),s*0.21,0.24,0.5);b.rotation.z=Math.PI/2-s*0.1;head.add(b)});
if(!animal){var nose=sph(0.065,shade(skin,0.93),0,-0.07,0.56,1,1.1,1);head.add(nose)}
if(animal){head.add(sph(0.075,"#3a2a2a",0,-0.06,0.55,1.25,0.85,0.8))}
var smile=new THREE.Mesh(new THREE.TorusGeometry(0.11,0.02,8,24,Math.PI),M("#8a3040"));
smile.rotation.z=Math.PI;smile.position.set(0,-0.2,0.52);head.add(smile);
head.add(sph(0.075,"#ff9aa2",-0.34,-0.1,0.42,1,0.6,0.35));head.add(sph(0.075,"#ff9aa2",0.34,-0.1,0.42,1,0.6,0.35));
if(!animal&&!robot&&cfg.sp!=="alien"){[-1,1].forEach(function(s){head.add(sph(0.09,shade(skin,0.97),s*0.55,-0.02,0,0.6,1,0.8))})}
// hair
if(!animal&&!robot&&cfg.sp!=="alien"){
 var hs=cfg.hs,hc=cfg.hair;
 if(hs!=="none"&&hs!=="spiky"){
  var cap=mesh(new THREE.SphereGeometry(0.6,36,20,0,Math.PI*2,0,Math.PI*0.55),(hs==="cap"||hs==="hat")?cfg.body:hc,0,0.03,-0.04,0.7);cap.rotation.x=-0.3;head.add(cap);
  if(hs==="short"||hs==="long"){[-0.34,-0.17,0,0.17,0.34].forEach(function(x,i){head.add(sph(0.13,hc,x,0.4-Math.abs(x)*0.25,0.4,1,0.8,0.7))})}
  if(hs==="long"){head.add(sph(0.56,hc,0,-0.35,-0.28,1,1.5,0.7));[-1,1].forEach(function(s){head.add(sph(0.14,hc,s*0.5,-0.3,0.1,0.8,2.4,0.9))})}
  if(hs==="cap"){var br=mesh(new THREE.CylinderGeometry(0.38,0.38,0.05,28,1,false,0,Math.PI),cfg.body,0,0.2,0.56);br.rotation.x=0.12;head.add(br)}
  if(hs==="hat"){head.add(mesh(new THREE.CylinderGeometry(0.5,0.5,0.05,30),cfg.body,0,0.5,0));head.add(mesh(new THREE.CylinderGeometry(0.3,0.33,0.4,26),cfg.body,0,0.72,0))}
 }
 if(hs==="spiky"){head.add(mesh(new THREE.SphereGeometry(0.6,32,16,0,Math.PI*2,0,Math.PI*0.45),hc,0,0.05,-0.05,0.7));for(var i=0;i<8;i++){var a=-1.3+i*0.37;var c=mesh(new THREE.ConeGeometry(0.13,0.42,10),hc,Math.sin(a)*0.45,0.55+Math.cos(a)*0.1,-0.02,0.7);c.rotation.z=-a*0.85;head.add(c)}}
}
// ears
var ec=animal?cfg.skin:cfg.hair;
if(cfg.ears==="cat"||cfg.sp==="cat"){[-1,1].forEach(function(s){var c=mesh(new THREE.ConeGeometry(0.2,0.42,4),ec,s*0.36,0.55,0);c.rotation.z=-s*0.35;head.add(c);var d=mesh(new THREE.ConeGeometry(0.1,0.28,4),"#ffb6c1",s*0.36,0.53,0.07);d.rotation.z=-s*0.35;head.add(d)})}
if(cfg.ears==="bunny"||cfg.sp==="bunny"){[-1,1].forEach(function(s){var c=sph(0.13,ec,s*0.2,0.98,0,1,3.4,0.7);c.rotation.z=-s*0.12;head.add(c);var d=sph(0.07,"#ffb6c1",s*0.2,0.98,0.06,1,3,0.5);d.rotation.z=-s*0.12;head.add(d)})}
if(cfg.ears==="bear"||cfg.sp==="bear"){[-1,1].forEach(function(s){head.add(sph(0.2,ec,s*0.44,0.42,0));head.add(sph(0.1,"#e8b7a0",s*0.44,0.42,0.1))})}
if(cfg.ears==="dog"||cfg.sp==="dog"){[-1,1].forEach(function(s){var c=sph(0.15,cfg.hair,s*0.58,0.05,0,1,2.4,1);c.rotation.z=s*0.2;head.add(c)})}
// accessories
if(cfg.acc==="glasses"){[-1,1].forEach(function(s){var r=new THREE.Mesh(new THREE.TorusGeometry(0.16,0.02,10,28),M("#222222"));r.position.set(s*0.21,0.05,0.56);head.add(r)});head.add(mesh(new THREE.BoxGeometry(0.1,0.02,0.02),"#222222",0,0.07,0.58))}
if(cfg.acc==="antenna"||robot){head.add(mesh(new THREE.CylinderGeometry(0.02,0.02,0.4,8),"#555555",0,0.7,0));head.add(sph(0.07,"#ff4d4d",0,0.92,0))}
if(cfg.acc==="horns"||cfg.sp==="monster"){[-1,1].forEach(function(s){var c=mesh(new THREE.ConeGeometry(0.1,0.4,12),"#f4e3c1",s*0.32,0.55,0);c.rotation.z=-s*0.4;head.add(c)})}
if(cfg.acc==="crown"){head.add(mesh(new THREE.CylinderGeometry(0.3,0.34,0.18,20),"#ffd23f",0,0.66,0,0.3));for(var j=0;j<5;j++){var an=j/5*Math.PI*2;head.add(mesh(new THREE.ConeGeometry(0.06,0.18,8),"#ffd23f",Math.cos(an)*0.3,0.84,Math.sin(an)*0.3,0.3))}}
if(cfg.acc==="wings"){[-1,1].forEach(function(s){var w=sph(0.45,"#ffffff",s*0.8,1.9,-0.3,0.3,1.2,0.1);w.rotation.z=-s*0.5;G.add(w)})}
if(cfg.acc==="tail"||cfg.sp==="cat"||cfg.sp==="dog"){var tl=mesh(new THREE.CylinderGeometry(0.06,0.1,0.8,12),animal?cfg.skin:cfg.pants,0,1.2,-0.5);tl.rotation.x=-0.9;G.add(tl);G.add(sph(0.08,animal?cfg.skin:cfg.pants,0,1.5,-0.88))}
// interaction
var ry=0.35,rx=0,zoom=9,P={},pd=0,el0=ren.domElement;
el0.addEventListener("pointerdown",function(e){P[e.pointerId]=[e.clientX,e.clientY]});
el0.addEventListener("pointermove",function(e){
 var k=Object.keys(P);if(!P[e.pointerId])return;
 if(k.length===1){ry+=(e.clientX-P[e.pointerId][0])*0.01;rx+=(e.clientY-P[e.pointerId][1])*0.004;rx=Math.max(-0.35,Math.min(0.35,rx))}
 P[e.pointerId]=[e.clientX,e.clientY];
 if(k.length===2){var a=P[k[0]],b=P[k[1]],d=Math.hypot(a[0]-b[0],a[1]-b[1]);if(pd)zoom=Math.max(5,Math.min(14,zoom-(d-pd)*0.03));pd=d}});
function up(e){delete P[e.pointerId];pd=0}
el0.addEventListener("pointerup",up);el0.addEventListener("pointercancel",up);
el0.addEventListener("wheel",function(e){zoom=Math.max(5,Math.min(14,zoom+e.deltaY*0.005));e.preventDefault()},{passive:false});
var t0=Date.now();
function loop(){requestAnimationFrame(loop);var t=(Date.now()-t0)/1000;
 if(innerWidth!==lw||innerHeight!==lh){lw=innerWidth;lh=innerHeight;if(lw>0&&lh>0){cam.aspect=lw/lh;cam.updateProjectionMatrix();ren.setSize(lw,lh)}}
 if(lw<1||lh<1)return;
 G.rotation.y=ry;G.rotation.x=rx;G.position.y=Math.sin(t*2)*0.025;
 head.rotation.z=Math.sin(t*1.3)*0.05;head.rotation.y=Math.sin(t*0.9)*0.15;
 armR.rotation.z=0.2+Math.abs(Math.sin(t*2))*1.9;armR.userData.el.rotation.z=0.5+Math.sin(t*4)*0.35;
 armL.rotation.z=-0.15-Math.sin(t*2)*0.04;armL.userData.el.rotation.z=-0.15;
 var b=(Math.sin(t*1.1)>0.985)?0.1:1;eyeL.scale.y=b;eyeR.scale.y=b;
 cam.position.set(0,2.0,zoom);cam.lookAt(0,1.5,0);ren.render(scene,cam);
 if(!window.__dbg){try{var gl=ren.getContext();window.__dbg="size:"+lw+"x"+lh+" gl:"+gl.getParameter(gl.VERSION)+" err:"+gl.getError()+" lost:"+gl.isContextLost()}catch(e){window.__dbg="gl?"}}}
loop();
addEventListener("resize",function(){cam.aspect=innerWidth/innerHeight;cam.updateProjectionMatrix();ren.setSize(innerWidth,innerHeight)});
</script></body></html>"""

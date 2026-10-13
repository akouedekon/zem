// Focused active security checks against an isolated local instance only.
import { mkdir, writeFile } from 'node:fs/promises';
const base = new URL(process.env.SECURITY_BASE_URL || 'http://127.0.0.1:18080');
if (!['localhost', '127.0.0.1', '[::1]'].includes(base.hostname)) throw new Error('These probes require an isolated local instance.');
const checks=[];
async function check(name,path,expected,{token,method='GET',body}={}) {
  await new Promise(resolve=>setTimeout(resolve,150));
  const response=await fetch(new URL(path,base),{method,headers:{...(token?{Authorization:`Bearer ${token}`} : {}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});
  const data=await response.json().catch(()=>null);
  checks.push({name,status:response.status,expected,passed:expected.includes(response.status)});
  return data;
}
const nonce=Date.now(),password='Local-security-probe-2026';
async function account(suffix) {
  const email=`security-${nonce}-${suffix}@zem.test`;
  const user=await check(`Registration cannot escalate ${suffix}`,'/api/auth/register-passenger',[201],{method:'POST',body:{email,password,role:'ADMIN'}});
  checks.push({name:`Server assigns PASSENGER to ${suffix}`,passed:user?.role==='PASSENGER'});
  const auth=await check(`Login ${suffix}`,'/api/auth/login',[200],{method:'POST',body:{email,password}});
  return auth.token;
}
await check('Anonymous admin access','/api/admin/dossiers',[401]);
await check('Anonymous private documents','/api/documents/00000000-0000-0000-0000-000000000001',[401]);
await check('Forged session token','/api/auth/me',[401],{token:'forged-token'});
await check('SQL metacharacters cannot authenticate','/api/auth/login',[400,401],{method:'POST',body:{email:"' OR 1=1--",password}});
await check('Unknown public QR','/api/public/cards/not-a-token',[404]);
await check('Actuator environment is protected','/actuator/env',[401,403,404]);
const first=await account('first'),second=await account('second');
await check('Passenger cannot read admin revenue','/api/admin/mobility?day=2026-10-13',[403],{token:first});
await check('Passenger cannot read driver earnings','/api/driver/earnings?day=2026-10-13',[403],{token:first});
await check('Passenger cannot enable driver GPS','/api/driver/service',[403],{token:first,method:'PUT',body:{online:true,latitude:6.36,longitude:2.4}});
await check('Invalid coordinate rejected','/api/passenger/rides',[400],{token:first,method:'POST',body:{pickup:'test',destination:'test',pickupLat:999,pickupLon:2.4,destinationLat:6.37,destinationLon:2.43}});
const ride=await check('Create isolated test ride','/api/passenger/rides',[201],{token:first,method:'POST',body:{pickup:'Security fixture',destination:'Security fixture',pickupLat:6.36,pickupLon:2.4,destinationLat:6.37,destinationLon:2.43}});
await check('Another passenger cannot cancel ride',`/api/rides/${ride.id}/cancel`,[403,404],{token:second,method:'POST'});
await check('Another passenger cannot read GPS',`/api/rides/${ride.id}/location`,[403,404],{token:second});
await check('Malformed ride UUID rejected','/api/rides/not-a-uuid/cancel',[400],{token:first,method:'POST'});
await check('Owner cancels test ride',`/api/rides/${ride.id}/cancel`,[200],{token:first,method:'POST'});
await check('Logout revokes session','/api/auth/logout',[204],{token:first,method:'POST'});
await check('Revoked session cannot replay','/api/auth/me',[401],{token:first});
await check('Logout second test account','/api/auth/logout',[204],{token:second,method:'POST'});
const report={timestamp:new Date().toISOString(),scope:base.origin,limitations:'Focused automated local probes, not an independent or exhaustive penetration test.',checks};
await mkdir('security-results',{recursive:true});
await writeFile('security-results/local-probes.json',JSON.stringify(report,null,2));
console.log(`${checks.filter(c=>c.passed).length}/${checks.length} security checks passed`);
for(const c of checks.filter(c=>!c.passed)) console.error(c);
if(checks.some(c=>!c.passed)) process.exitCode=1;

const fs = require('fs');
const path = require('path');
const qrcode = require('../tools/qrcode.js');
const output = path.join(__dirname, '../test-output');
fs.mkdirSync(output, {recursive:true});
const key = 'AIza' + '01234567890123456789012345678901234';
const payloads = [key, JSON.stringify({apiKey:key, model:'gemini-3.5-flash-lite'})];
const matrices = payloads.map(text => {
  const qr=qrcode(0,'M');qr.addData(text,'Byte');qr.make();
  return {text, modules:Array.from({length:qr.getModuleCount()},(_,r)=>Array.from({length:qr.getModuleCount()},(_,c)=>qr.isDark(r,c)))};
});
fs.writeFileSync(path.join(output,'qr-matrices.json'),JSON.stringify(matrices));
console.log('Created 2 synthetic QR matrices with the shipped offline generator.');

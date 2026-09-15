function n(n){if(null==n)return"未定价";const[r,t=""]=n.split(".");return`¥ ${r.replace(/\B(?=(\d{3})+(?!\d))/g,",")}.${t.padEnd(4,"0")}`}export{n as f};

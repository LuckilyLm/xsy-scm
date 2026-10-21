import {createApp} from 'vue';
import {createRouter,createWebHashHistory} from 'vue-router';
import {ConfigProvider,Button,Input,InputNumber,Select,Switch,Form,Modal,Alert,Tag,Table,Spin} from 'ant-design-vue';
import 'ant-design-vue/dist/reset.css';
import './styles.css';
import App from './App.vue';
import {initializeAnalytics} from './analytics';

const router=createRouter({history:createWebHashHistory(),routes:[
  {path:'/',redirect:'/tasks'},
  {path:'/tasks',component:()=>import('./pages/Tasks.vue')},
  {path:'/tasks/:mode(products|customers)/:group?',component:()=>import('./pages/Tasks.vue')},
  {path:'/weigh/:jobId/:taskItemId',component:()=>import('./pages/Weigh.vue')},
  {path:'/records',component:()=>import('./pages/Records.vue')},
  {path:'/settings',component:()=>import('./pages/Settings.vue')},
  {path:'/:pathMatch(.*)*',redirect:'/tasks'}
]});
initializeAnalytics();
const app=createApp(App).use(router);
for(const component of [ConfigProvider,Button,Input,InputNumber,Select,Switch,Form,Modal,Alert,Tag,Table,Spin])app.use(component);
app.mount('#app');

import type RequestError from '../components/support/request-error/index.vue';

declare module 'vue' {
  export interface GlobalComponents {
    RequestError: typeof RequestError;
  }
}

export {};

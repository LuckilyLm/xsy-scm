import { getRequest, postRequest } from '@/lib/smart-request';

export const mallAuthApi = {
  login: (form) => postRequest('/scm/mall/auth/login', form),
  logout: () => postRequest('/scm/mall/auth/logout'),
  getProfile: () => getRequest('/scm/mall/auth/profile'),
  changePassword: (form) => postRequest('/scm/mall/auth/password/change', form),
};

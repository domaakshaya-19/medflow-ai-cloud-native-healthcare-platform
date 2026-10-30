import { api } from '../api';

export const laboratoryApi = {
  createOrder: (order) => api.post('/api/lab/orders', order).then(({ data }) => data),
  myOrders: () => api.get('/api/lab/orders/me').then(({ data }) => data),
  orders: () => api.get('/api/lab/orders').then(({ data }) => data),
  order: (id) => api.get(`/api/lab/orders/${id}`).then(({ data }) => data),
  updateOrder: (id, order) => api.put(`/api/lab/orders/${id}`, order).then(({ data }) => data),
  removeOrder: (id) => api.delete(`/api/lab/orders/${id}`),
  createResult: (result) => api.post('/api/lab/results', result).then(({ data }) => data),
  myResults: () => api.get('/api/lab/results/me').then(({ data }) => data),
  results: () => api.get('/api/lab/results').then(({ data }) => data),
  resultForOrder: (orderId) => api.get(`/api/lab/results/order/${orderId}`).then(({ data }) => data),
  result: (id) => api.get(`/api/lab/results/${id}`).then(({ data }) => data),
  updateResult: (id, result) => api.put(`/api/lab/results/${id}`, result).then(({ data }) => data),
};

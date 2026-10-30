import { api } from '../api';

export const prescriptionsApi = {
  create: (prescription) => api.post('/api/prescriptions', prescription).then(({ data }) => data),
  mine: () => api.get('/api/prescriptions/me').then(({ data }) => data),
  patientMine: () => api.get('/api/prescriptions/patient/me').then(({ data }) => data),
  doctorMine: () => api.get('/api/prescriptions/doctor/me').then(({ data }) => data),
  byAppointment: (appointmentId) => api.get(`/api/prescriptions/appointment/${appointmentId}`).then(({ data }) => data),
  get: (id) => api.get(`/api/prescriptions/${id}`).then(({ data }) => data),
  list: () => api.get('/api/prescriptions').then(({ data }) => data),
  update: (id, prescription) => api.put(`/api/prescriptions/${id}`, prescription).then(({ data }) => data),
  cancel: (id) => api.delete(`/api/prescriptions/${id}`).then(({ data }) => data),
};

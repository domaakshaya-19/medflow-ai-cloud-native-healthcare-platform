import { useCallback, useEffect, useState } from 'react';
import { appointmentsApi } from '../api/appointments';
import { doctorsApi } from '../api/doctors';
import { laboratoryApi } from '../api/laboratory';
import { medicalRecordsApi } from '../api/medicalRecords';
import { patientsApi } from '../api/patients';
import { prescriptionsApi } from '../api/prescriptions';
import ErrorAlert from '../components/ui/ErrorAlert';
import LoadingState from '../components/ui/LoadingState';
import { useAuth } from '../context/AuthContext';

const roleLabels = { PATIENT: 'Patient workspace', DOCTOR: 'Clinical workspace', ADMIN: 'Administration workspace' };
export default function Dashboard() {
  const { user } = useAuth();
  const [state, setState] = useState({ loading: true, cards: [], notices: [] });
  const load = useCallback(async () => {
    setState((current) => ({ ...current, loading: true }));
    const definitions = dashboardRequests(user.role);
    const results = await Promise.allSettled(definitions.map((item) => item.request()));
    const cards = []; const notices = [];
    results.forEach((result, index) => {
      const definition = definitions[index];
      if (result.status === 'fulfilled') cards.push({ ...definition, value: definition.value(result.value) });
      else if (result.reason?.response?.status === 404 && definition.profile) notices.push(definition.missingMessage);
      else notices.push(`${definition.label} could not be loaded${result.reason?.response?.status ? ` (HTTP ${result.reason.response.status})` : ''}.`);
    });
    setState({ loading: false, cards, notices: [...new Set(notices)] });
  }, [user.role]);
  useEffect(() => { load(); }, [load]);
  return <><section className="page-heading"><div><p className="eyebrow">{roleLabels[user.role] || 'MedFlow workspace'}</p>
    <h1>Welcome, {user.fullName.split(' ')[0]}</h1><p>Live information is shown only when its backend service responds successfully.</p></div>
    <span className="session-chip"><span className="secure-dot" /> Authenticated</span></section>
    <section className="hero-card"><div><span className="hero-kicker">Connected care</span><h2>Your MedFlow overview</h2>
      <p>Review the clinical modules available for your role. Counts below come directly from the API Gateway.</p></div><div className="hero-mark">M+</div></section>
    {state.loading ? <LoadingState label="Loading your healthcare summary…" /> : <><div className="notice-stack">{state.notices.map((notice) => <ErrorAlert key={notice} message={notice} />)}</div>
      <section><div className="section-heading"><h2>Live summary</h2><button className="text-button" onClick={load}>Refresh</button></div>
        {state.cards.length ? <div className="metric-grid">{state.cards.map((card) => <article className="metric-card" key={card.key}><span>{card.label}</span><strong>{card.value}</strong><small>{card.description}</small></article>)}</div>
          : <p className="muted">No clinical service returned dashboard data.</p>}</section></>}</>;
}
function dashboardRequests(role) {
  if (role === 'PATIENT') return [profile('profile','Patient profile',patientsApi.me,'Your required Patient Profile has not been created yet.'),list('appointments','Appointments',appointmentsApi.mine,'Your appointments'),list('records','Medical records',medicalRecordsApi.mine,'Authorized records'),list('lab-orders','Lab orders',laboratoryApi.myOrders,'Orders visible to you'),list('lab-results','Lab results',laboratoryApi.myResults,'Results available'),list('prescriptions','Prescriptions',prescriptionsApi.patientMine,'Prescriptions visible to you')];
  if (role === 'DOCTOR') return [profile('profile','Doctor profile',doctorsApi.me,'Your Doctor Profile has not been created yet.'),list('appointments','Appointments',appointmentsApi.doctorMine,'Assigned appointments'),list('queue','Today’s queue',appointmentsApi.doctorQueue,'Active queue entries'),list('records','Medical records',medicalRecordsApi.doctorMine,'Records under your care'),list('lab-orders','Lab orders',laboratoryApi.orders,'Orders under your care'),list('prescriptions','Prescriptions',prescriptionsApi.doctorMine,'Prescriptions authored')];
  if (role === 'ADMIN') return [list('patients','Patients',patientsApi.list,'Registered patient profiles'),list('doctors','Doctors',doctorsApi.list,'Registered doctor profiles'),list('appointments','Appointments',appointmentsApi.list,'All appointments'),list('records','Medical records',medicalRecordsApi.doctorMine,'All medical records'),list('lab-orders','Lab orders',laboratoryApi.orders,'All laboratory orders'),list('prescriptions','Prescriptions',prescriptionsApi.list,'All prescriptions')];
  return [];
}
function list(key,label,request,description){return{key,label,request,description,value:(value)=>Array.isArray(value)?value.length:0};}
function profile(key,label,request,missingMessage){return{key,label,request,missingMessage,profile:true,description:'Clinical identity linked',value:()=> 'Ready'};}

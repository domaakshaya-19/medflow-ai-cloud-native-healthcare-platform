import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiError } from '../../api';
import { appointmentsApi } from '../../api/appointments';
import { doctorsApi } from '../../api/doctors';
import EmptyState from '../../components/ui/EmptyState';
import ErrorAlert from '../../components/ui/ErrorAlert';
import LoadingState from '../../components/ui/LoadingState';
import StatusBadge from '../../components/ui/StatusBadge';
import { useAuth } from '../../context/AuthContext';

export default function AppointmentsPage() {
  const { user } = useAuth(); const [state, setState] = useState({ loading: true, appointments: [], queue: [], doctors: [], errors: [] });
  const load = useCallback(async () => {
    setState((current) => ({ ...current, loading: true, errors: [] }));
    const requests = [roleAppointments(user.role)(), doctorsApi.directory()];
    if (user.role === 'DOCTOR') requests.push(appointmentsApi.doctorQueue());
    const results = await Promise.allSettled(requests); const errors = [];
    const appointments = resultValue(results[0], [], errors, 'Appointments');
    const doctors = resultValue(results[1], [], errors, 'Doctor information');
    const queue = user.role === 'DOCTOR' ? resultValue(results[2], [], errors, 'Today’s queue') : [];
    setState({ loading: false, appointments, doctors, queue, errors });
  }, [user.role]);
  useEffect(() => { load(); }, [load]);
  const doctorMap = useMemo(() => new Map(state.doctors.map((doctor) => [doctor.id, doctor])), [state.doctors]);
  if (state.loading) return <LoadingState label="Loading appointments…" />;
  return <><section className="page-heading"><div><p className="eyebrow">Scheduling</p><h1>{user.role === 'DOCTOR' ? 'My appointments' : user.role === 'ADMIN' ? 'Appointment administration' : 'My appointments'}</h1>
    <p>{user.role === 'PATIENT' ? 'Review your bookings and their current status.' : user.role === 'DOCTOR' ? 'Manage your assigned appointment workflow.' : 'Review all appointments permitted by the administration API.'}</p></div>
    {user.role === 'PATIENT' && <Link className="button-link" to="/doctors">Find a doctor</Link>}</section>
    <div className="notice-stack">{state.errors.map((error) => <ErrorAlert key={error} message={error} onRetry={load} />)}</div>
    {user.role === 'DOCTOR' && <AppointmentSection title="Today’s active queue" appointments={state.queue} doctorMap={doctorMap} empty="There are no active appointments in today’s queue." queue />}
    <AppointmentSection title={user.role === 'DOCTOR' ? 'All assigned appointments' : 'Appointments'} appointments={state.appointments} doctorMap={doctorMap}
      empty={user.role === 'PATIENT' ? 'You have not booked an appointment yet.' : 'No appointments are available.'} />
  </>;
}

function AppointmentSection({ title, appointments, doctorMap, empty, queue }) { return <section className="appointment-section"><div className="section-heading"><h2>{title}</h2><span>{appointments.length}</span></div>
  {!appointments.length ? <EmptyState message={empty} /> : <div className="appointment-list">{appointments.map((appointment) => <AppointmentRow key={appointment.id} appointment={appointment} doctor={doctorMap.get(appointment.doctorId)} queue={queue} />)}</div>}</section>; }
function AppointmentRow({ appointment, doctor, queue }) { return <article className="appointment-row"><div className="appointment-date"><strong>{day(appointment.appointmentDate)}</strong><span>{month(appointment.appointmentDate)}</span></div>
  <div className="appointment-main"><div><h3>{doctor ? `Dr. ${doctor.firstName} ${doctor.lastName}` : 'Doctor information unavailable'}</h3><p>{doctor?.specialization || appointment.reason}</p></div>
    <div className="appointment-meta"><span>{formatDate(appointment.appointmentDate)}</span><span>{formatTime(appointment.appointmentTime)}</span>{(queue || appointment.queueNumber) && <span>Queue #{appointment.queueNumber}</span>}</div></div>
  <div className="appointment-actions"><StatusBadge status={appointment.status}/><Link to={`/appointments/${appointment.id}`}>View details →</Link></div></article>; }
function roleAppointments(role){if(role==='PATIENT')return appointmentsApi.mine;if(role==='DOCTOR')return appointmentsApi.doctorMine;return appointmentsApi.list;}
function resultValue(result,fallback,errors,label){if(result.status==='fulfilled')return result.value;errors.push(`${label} could not be loaded${result.reason?.response?.status?` (HTTP ${result.reason.response.status})`:''}.`);return fallback;}
function parsedDate(value){return new Date(`${value}T00:00:00`);} function day(value){return String(parsedDate(value).getDate()).padStart(2,'0');} function month(value){return parsedDate(value).toLocaleDateString(undefined,{month:'short'});} function formatDate(value){return parsedDate(value).toLocaleDateString(undefined,{year:'numeric',month:'long',day:'numeric'});} function formatTime(value){return String(value).slice(0,5);}

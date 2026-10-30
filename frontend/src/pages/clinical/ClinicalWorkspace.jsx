import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { apiError } from '../../api';
import { appointmentsApi } from '../../api/appointments';
import { medicalRecordsApi } from '../../api/medicalRecords';
import { laboratoryApi } from '../../api/laboratory';
import { prescriptionsApi } from '../../api/prescriptions';
import { useAuth } from '../../context/AuthContext';
import DataTable from '../../components/ui/DataTable';
import ErrorAlert from '../../components/ui/ErrorAlert';
import LoadingState from '../../components/ui/LoadingState';
import StatusBadge from '../../components/ui/StatusBadge';
import './clinical.css';

const labels = { records: 'Medical records', laboratory: 'Laboratory', prescriptions: 'Prescriptions' };
const date = () => new Date().toLocaleDateString('en-CA');
const initial = { appointmentId: '', diagnosis: '', symptoms: '', treatmentPlan: '', medications: '', notes: '', visitDate: date(), testName: '', instructions: '' };
const medicine = () => ({ medicineName: '', dosage: '', frequency: '', duration: '', instructions: '' });

export default function ClinicalWorkspace({ kind }) {
  const { user } = useAuth(); const [params] = useSearchParams();
  const canManage = ['DOCTOR', 'ADMIN'].includes(user.role);
  const canPublishLab = canManage || (kind === 'laboratory' && user.role === 'LAB_STAFF');
  const [state, setState] = useState({ loading: true, rows: [], results: [], appointments: [], error: '' });
  const [selected, setSelected] = useState(null); const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ ...initial, appointmentId: params.get('appointmentId') || '' });
  const [medicines, setMedicines] = useState([medicine()]);
  const [result, setResult] = useState({ orderId: '', resultSummary: '', details: '', referenceRange: '', abnormal: false });
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [success, setSuccess] = useState('');
  const load = useCallback(async () => {
    setState(s => ({ ...s, loading: true, error: '' }));
    try {
      const patient = user.role === 'PATIENT';
      const list = kind === 'records' ? (patient ? medicalRecordsApi.mine : medicalRecordsApi.doctorMine)
        : kind === 'prescriptions' ? (patient ? prescriptionsApi.patientMine : user.role === 'ADMIN' ? prescriptionsApi.list : prescriptionsApi.doctorMine)
          : (patient ? laboratoryApi.myOrders : laboratoryApi.orders);
      const [rows, results, appointments] = await Promise.all([list(), kind === 'laboratory' ? (patient ? laboratoryApi.myResults() : laboratoryApi.results()) : [],
        canManage ? (user.role === 'ADMIN' ? appointmentsApi.list() : appointmentsApi.doctorMine()) : []]);
      setState({ loading: false, rows, results, appointments, error: '' });
    } catch (e) { setState(s => ({ ...s, loading: false, error: apiError(e, `${labels[kind]} could not be loaded.`).message })); }
  }, [kind, user.role, canManage]);
  useEffect(() => { setSelected(null); setEditing(false); setForm({ ...initial, appointmentId: params.get('appointmentId') || '' }); setSuccess(''); setError(''); load(); }, [load, params]);
  const eligible = state.appointments.filter(a => (kind === 'laboratory' ? ['CONFIRMED', 'IN_PROGRESS', 'COMPLETED'] : ['IN_PROGRESS', 'COMPLETED']).includes(a.status));
  const change = e => setForm(f => ({ ...f, [e.target.name]: e.target.value }));
  async function perform(action, message) {
    setBusy(true); setError(''); setSuccess('');
    try { await action(); setSuccess(message); setSelected(null); setEditing(false); await load(); }
    catch (e) { setError(apiError(e, 'The change could not be saved. No success was assumed.').message); }
    finally { setBusy(false); }
  }
  function create(e) {
    e.preventDefault(); const appointment = eligible.find(a => a.id === form.appointmentId);
    if (!appointment) { setError('Choose an eligible appointment from your authorized list.'); return; }
    const reference = { patientId: appointment.patientId, doctorId: appointment.doctorId, appointmentId: appointment.id };
    const payload = kind === 'records' ? { ...reference, diagnosis: form.diagnosis, symptoms: form.symptoms, treatmentPlan: form.treatmentPlan, medications: form.medications, notes: form.notes, visitDate: form.visitDate }
      : kind === 'prescriptions' ? { ...reference, diagnosis: form.diagnosis, notes: form.notes, medicines }
        : { ...reference, testName: form.testName, instructions: form.instructions };
    perform(() => kind === 'records' ? medicalRecordsApi.create(payload) : kind === 'prescriptions' ? prescriptionsApi.create(payload) : laboratoryApi.createOrder(payload), `${labels[kind]} saved successfully.`);
  }
  const summary = row => kind === 'records' || kind === 'prescriptions' ? row.diagnosis : row.testName;
  return <><section className="page-heading"><div><p className="eyebrow">{user.role === 'PATIENT' ? 'Your clinical history' : 'Authorized clinical workspace'}</p><h1>{labels[kind]}</h1><p>{canManage ? 'Work with assigned appointments. Every change is authorized again by the backend.' : 'View only the information associated with your patient profile.'}</p></div><button className="secondary" onClick={load} disabled={busy}>Refresh</button></section>
    {success && <p className="success" role="status">{success}</p>}<ErrorAlert message={error} />
    {state.loading ? <LoadingState label={`Loading ${labels[kind].toLowerCase()}…`} /> : state.error ? <ErrorAlert message={state.error} onRetry={load} /> : <>
      <DataTable rows={state.rows} columns={[{ key: 'summary', label: kind === 'laboratory' ? 'Test' : 'Diagnosis', render: summary }, { key: 'date', label: 'Date', render: r => r.visitDate || (r.prescriptionDate || r.orderedAt || r.createdAt || '').slice(0, 10) }, { key: 'status', label: 'Status', render: r => r.status ? <StatusBadge status={r.status} /> : 'Recorded' }, { key: 'details', label: 'Details', render: r => <button className="text-button" onClick={() => { setSelected(r); setEditing(false); }}>View details</button> }]} emptyMessage="No authorized records were returned." />
      {selected && <section className="clinical-card" aria-label="Selected clinical record"><div className="section-heading"><h2>{summary(selected)}</h2><button className="secondary" onClick={() => setSelected(null)}>Close details</button></div><dl className="clinical-details">{Object.entries(selected).filter(([key]) => key !== 'medicines').map(([key, value]) => <div key={key}><dt>{key.replace(/([A-Z])/g, ' $1')}</dt><dd>{value == null ? 'Not provided' : String(value)}</dd></div>)}</dl>
        {selected.medicines && <DataTable rows={selected.medicines.map((m, i) => ({ ...m, rowId: i }))} keyField="rowId" columns={['medicineName', 'dosage', 'frequency', 'duration', 'instructions'].map(key => ({ key, label: key.replace(/([A-Z])/g, ' $1') }))} />}
        {canPublishLab && kind === 'laboratory' && <div className="action-row">{['COLLECTED', 'COMPLETED', 'CANCELLED'].filter(s => s !== selected.status).map(status => <button key={status} disabled={busy} onClick={() => { if (window.confirm(`Change this lab order to ${status}?`)) perform(() => laboratoryApi.updateOrder(selected.id, { status }), 'Lab order status updated.'); }}>{status}</button>)}</div>}
        {canManage && kind === 'records' && <><button className="secondary" onClick={() => { setForm(f => ({ ...f, ...selected })); setEditing(true); }}>Edit clinical record</button>{editing && <form onSubmit={e => { e.preventDefault(); perform(() => medicalRecordsApi.update(selected.id, { diagnosis: form.diagnosis, symptoms: form.symptoms, treatmentPlan: form.treatmentPlan, medications: form.medications, notes: form.notes, visitDate: form.visitDate }), 'Clinical record updated.'); }}><RecordFields form={form} change={change} /><button disabled={busy}>Save clinical record</button></form>}</>}
      </section>}
      {kind === 'laboratory' && <section className="clinical-card"><h2>Results</h2><DataTable rows={state.results} columns={[{ key: 'resultSummary', label: 'Result summary' }, { key: 'details', label: 'Details' }, { key: 'referenceRange', label: 'Reference range' }, { key: 'abnormal', label: 'Flag', render: r => r.abnormal ? 'Flagged for clinician review' : 'Not flagged' }, { key: 'resultDate', label: 'Reported', render: r => r.resultDate?.slice(0, 10) || 'Not provided' }]} emptyMessage="No authorized laboratory results yet." /></section>}
      {canManage && <section className="clinical-card"><h2>{kind === 'records' ? 'Create clinical record' : kind === 'prescriptions' ? 'Create prescription' : 'Order a laboratory test'}</h2><p>Use synthetic demonstration data only. {kind === 'laboratory' ? 'A confirmed or active appointment is required.' : 'The appointment must be in progress or completed; only one record per appointment is accepted.'}</p>
        {eligible.length ? <form onSubmit={create}><fieldset disabled={busy}><label className="field"><span>Authorized appointment</span><select required name="appointmentId" value={form.appointmentId} onChange={change}><option value="">Choose an appointment</option>{eligible.map(a => <option key={a.id} value={a.id}>{a.appointmentDate} {String(a.appointmentTime).slice(0, 5)} · Queue {a.queueNumber} · {a.reason}</option>)}</select></label>
          {kind === 'records' ? <RecordFields form={form} change={change} /> : kind === 'laboratory' ? <><Field name="testName" label="Test name" value={form.testName} onChange={change} required maxLength={250} /><Field name="instructions" label="Test instructions" value={form.instructions} onChange={change} maxLength={2000} /></> : <><Field name="diagnosis" label="Clinician diagnosis" value={form.diagnosis} onChange={change} required maxLength={2000} /><Field name="notes" label="Prescription notes" value={form.notes} onChange={change} maxLength={6000} />{medicines.map((m, i) => <fieldset className="medicine-row" key={i}><legend>Medicine {i + 1}</legend>{Object.keys(m).map(key => <Field key={key} name={key} label={key.replace(/([A-Z])/g, ' $1')} value={m[key]} required={key !== 'instructions'} maxLength={key === 'instructions' ? 1000 : key === 'medicineName' ? 250 : 150} onChange={e => setMedicines(items => items.map((item, index) => index === i ? { ...item, [key]: e.target.value } : item))} />)}{medicines.length > 1 && <button type="button" className="secondary" onClick={() => setMedicines(items => items.filter((_, index) => index !== i))}>Remove medicine {i + 1}</button>}</fieldset>)}<button type="button" className="secondary" disabled={medicines.length >= 20} onClick={() => setMedicines(items => [...items, medicine()])}>Add medicine</button></>}
          <button disabled={busy}>{busy ? 'Saving…' : kind === 'laboratory' ? 'Create lab order' : kind === 'records' ? 'Create record' : 'Create prescription'}</button></fieldset></form> : <p className="muted">No eligible appointments. Confirm and start an assigned appointment from Appointments first.</p>}
      </section>}
      {canPublishLab && kind === 'laboratory' && <section className="clinical-card"><h2>Publish laboratory result</h2><form onSubmit={e => { e.preventDefault(); perform(() => laboratoryApi.createResult({ ...result, resultDate: new Date().toISOString() }), 'Laboratory result published.'); }}><fieldset disabled={busy}><label className="field"><span>Authorized lab order</span><select required value={result.orderId} onChange={e => setResult({ ...result, orderId: e.target.value })}><option value="">Choose an order without a result</option>{state.rows.filter(o => o.status !== 'CANCELLED' && !state.results.some(r => r.orderId === o.id)).map(o => <option key={o.id} value={o.id}>{o.testName} · {o.id.slice(0, 8)}</option>)}</select></label>{['resultSummary', 'details', 'referenceRange'].map(key => <Field key={key} label={key.replace(/([A-Z])/g, ' $1')} name={key} required={key === 'resultSummary'} maxLength={key === 'details' ? 6000 : key === 'referenceRange' ? 1000 : 2000} value={result[key]} onChange={e => setResult({ ...result, [key]: e.target.value })} />)}<label><input type="checkbox" checked={result.abnormal} onChange={e => setResult({ ...result, abnormal: e.target.checked })} /> Flag for clinician review</label><button disabled={busy}>Publish result</button></fieldset></form></section>}
    </>}
  </>;
}
function Field({ label, ...props }) { return <label className="field"><span>{label}</span><textarea rows={2} {...props} /></label>; }
function RecordFields({ form, change }) { return <><div className="form-grid">{['diagnosis', 'symptoms', 'treatmentPlan', 'medications', 'notes'].map(key => <Field key={key} label={key.replace(/([A-Z])/g, ' $1')} name={key} value={form[key]} onChange={change} required={key === 'diagnosis'} maxLength={key === 'diagnosis' ? 2000 : ['treatmentPlan', 'notes'].includes(key) ? 6000 : 4000} />)}</div><label className="field"><span>Visit date</span><input required type="date" name="visitDate" max={date()} value={form.visitDate} onChange={change} /></label></>; }

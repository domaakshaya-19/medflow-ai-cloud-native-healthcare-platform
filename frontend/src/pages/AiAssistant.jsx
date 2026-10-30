import { useEffect, useState } from 'react';
import { api, apiError } from '../api';
import ErrorAlert from '../components/ui/ErrorAlert';
import LoadingState from '../components/ui/LoadingState';

export default function AiAssistant() {
  const [capabilities, setCapabilities] = useState(null);
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  const [response, setResponse] = useState(null); const [agent, setAgent] = useState('');
  const [tool, setTool] = useState(''); const [argumentsByName, setArguments] = useState({});
  const [invokeModel, setInvokeModel] = useState(false); const [consent, setConsent] = useState(false);
  useEffect(() => { api.get('/api/ai/capabilities').then(({ data }) => setCapabilities(data)).catch(e => setError(apiError(e, 'Assistant service is unavailable.').message)); }, []);
  const selected = capabilities?.agents.find(a => a.name === agent);
  const selectedTool = selected?.tools.find(t => t.name === tool);
  async function submit(e) {
    e.preventDefault(); setBusy(true); setError(''); setResponse(null);
    try {
      const args = Object.fromEntries((selectedTool?.arguments || []).map(name => [name, argumentsByName[name] || '']));
      const { data } = await api.post('/api/ai/requests', { agent, tool, arguments: args, invokeModel, syntheticDemo: consent, consentToModel: consent });
      setResponse(data);
    } catch (e) { setError(apiError(e, 'Assistant request could not be completed.').message); }
    finally { setBusy(false); }
  }
  return <>
    <section className="page-heading"><div><p className="eyebrow">Controlled assistance</p><h1>AI assistant</h1><p>Read-only tools are authorized by the backend. No agent can silently modify clinical data.</p></div></section>
    <ErrorAlert message={error} />
    {!capabilities && !error ? <LoadingState label="Loading assistant capabilities…" /> : capabilities && <section className="clinical-card">
      <p role="status">Gemini: {capabilities.modelConfigured ? 'Configured; successful provider invocation is verified separately.' : 'Not configured — real model generation is blocked. Authorized read-only tools remain available.'}</p>
      <form onSubmit={submit}><fieldset disabled={busy}>
        <label className="field"><span>Agent</span><select required value={agent} onChange={e => { setAgent(e.target.value); setTool(''); setArguments({}); setResponse(null); }}><option value="">Choose an authorized agent</option>{capabilities.agents.map(a => <option key={a.name}>{a.name}</option>)}</select></label>
        {selected && <p>{selected.instruction}</p>}
        <label className="field"><span>Read-only tool</span><select required value={tool} onChange={e => { setTool(e.target.value); setArguments({}); }}><option value="">Choose a registered tool</option>{selected?.tools.map(t => <option key={t.name}>{t.name}</option>)}</select></label>
        {selected && !selected.tools.length && <p>No tools are authorized for your role on this agent.</p>}
        {selectedTool && <p>{selectedTool.ownership} · {selectedTool.permission}</p>}
        {selectedTool?.arguments.map(name => <label className="field" key={name}><span>{name.replace(/([A-Z])/g, ' $1')} UUID (resource access is checked by the backend)</span><input required maxLength={36} value={argumentsByName[name] || ''} onChange={e => setArguments(a => ({ ...a, [name]: e.target.value }))} /></label>)}
        <label><input type="checkbox" disabled={!capabilities.modelConfigured} checked={invokeModel} onChange={e => setInvokeModel(e.target.checked)} /> Ask configured Gemini to explain this workflow</label>
        {invokeModel && <label className="field"><span><input type="checkbox" required checked={consent} onChange={e => setConsent(e.target.checked)} /> I confirm a synthetic demonstration and consent to sending only the selected agent/tool name and aggregate item count to Gemini. Clinical text, identifiers and free-text prompts are not sent.</span></label>}
        <button disabled={busy || !tool}>{busy ? 'Retrieving…' : 'Run authorized read-only request'}</button>
      </fieldset></form>
    </section>}
    {response && <section className="clinical-card"><h2>{response.agent} · {response.mode}</h2><p>{response.explanation}</p><p className="privacy-note">{response.limitation}</p><pre className="tool-data">{JSON.stringify(response.data, null, 2)}</pre><small>Diagnostic event: {response.eventId}</small></section>}
  </>;
}

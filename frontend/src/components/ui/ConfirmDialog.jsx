export default function ConfirmDialog({ open, title, message, confirmLabel = 'Confirm', busy, onConfirm, onCancel }) {
  if (!open) return null;
  return <div className="dialog-backdrop" role="presentation" onMouseDown={onCancel}>
    <div className="dialog" role="alertdialog" aria-modal="true" aria-labelledby="dialog-title" onMouseDown={(event) => event.stopPropagation()}>
      <h2 id="dialog-title">{title}</h2><p>{message}</p>
      <div className="dialog-actions"><button type="button" className="secondary" onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="button" className="danger-button" onClick={onConfirm} disabled={busy}>{busy ? 'Working…' : confirmLabel}</button></div>
    </div>
  </div>;
}

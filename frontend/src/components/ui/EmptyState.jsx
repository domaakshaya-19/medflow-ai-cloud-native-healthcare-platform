export default function EmptyState({ title = 'Nothing here yet', message, action }) {
  return <div className="state-card empty-state">
    <span className="state-icon" aria-hidden="true">○</span>
    <strong>{title}</strong>
    {message && <p>{message}</p>}
    {action}
  </div>;
}

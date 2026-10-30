export default function ErrorAlert({ message, onRetry }) {
  if (!message) return null;
  return <div className="error alert-row" role="alert">
    <span>{message}</span>
    {onRetry && <button type="button" className="text-button" onClick={onRetry}>Try again</button>}
  </div>;
}

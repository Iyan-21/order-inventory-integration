export default function NotificationFeed({ notifications }) {
  const sorted = [...notifications].sort((a, b) => b.notificationId - a.notificationId);

  return (
    <div className="panel">
      <h2 className="panel-title">Activity feed</h2>

      {sorted.length === 0 ? (
        <p className="cart-empty">No activity yet.</p>
      ) : (
        <ul className="feed-list">
          {sorted.map((n) => (
            <li key={n.notificationId} className="feed-item">
              <span className="feed-message">{n.message}</span>
              <span className="feed-time mono">
                {new Date(n.createdAt).toLocaleTimeString()}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
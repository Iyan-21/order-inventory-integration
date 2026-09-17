import { useEffect, useState, useCallback } from 'react';
import './App.css';
import Cart from './components/Cart';
import InventoryTable from './components/InventoryTable';
import OrderHistory from './components/OrderHistory';
import NotificationFeed from './components/NotificationFeed';
import { getInventory, getOrders, getNotifications, placeOrder, cancelOrder } from './api';

function App() {
  const [inventory, setInventory] = useState([]);
  const [orders, setOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [banner, setBanner] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [cancellingId, setCancellingId] = useState(null);
  const [loading, setLoading] = useState(true);

  const refreshAll = useCallback(async () => {
    try {
      const [inv, ord, notif] = await Promise.all([
        getInventory(),
        getOrders(),
        getNotifications(),
      ]);
      setInventory(inv);
      setOrders(ord);
      setNotifications(notif);
    } catch (err) {
      setBanner({ type: 'error', message: err.message });
    }
  }, []);

  useEffect(() => {
    refreshAll().finally(() => setLoading(false));
  }, [refreshAll]);

  const handlePlaceOrder = async (items) => {
    setSubmitting(true);
    setBanner(null);
    try {
      const result = await placeOrder(items);
      setBanner({
        type: result.status === 'CONFIRMED' ? 'success' : 'error',
        message:
          result.status === 'CONFIRMED'
            ? `Order #${result.orderId} confirmed.`
            : `Order rejected: ${result.reason}`,
      });
      await refreshAll();
    } catch (err) {
      setBanner({ type: 'error', message: err.message });
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async (orderId) => {
    setCancellingId(orderId);
    setBanner(null);
    try {
      await cancelOrder(orderId);
      setBanner({ type: 'success', message: `Order #${orderId} cancelled and restocked.` });
      await refreshAll();
    } catch (err) {
      setBanner({ type: 'error', message: err.message });
    } finally {
      setCancellingId(null);
    }
  };

  if (loading) {
    return (
      <div className="console-wide">
        <p className="loading-text">Loading…</p>
      </div>
    );
  }

  return (
    <div className="console-wide">
      <div className="console-header">
        <div className="console-eyebrow">ORDER &amp; INVENTORY CONSOLE</div>
        <h1 className="console-title">Operations dashboard</h1>
      </div>

      {banner && (
        <div className={`error-banner ${banner.type === 'success' ? 'banner-success' : ''}`}>
          {banner.message}
        </div>
      )}

      <div className="dashboard-grid">
        <div className="dashboard-col">
          <Cart inventory={inventory} onSubmit={handlePlaceOrder} submitting={submitting} />
          <InventoryTable inventory={inventory} />
        </div>
        <div className="dashboard-col">
          <OrderHistory orders={orders} onCancel={handleCancel} cancellingId={cancellingId} />
          <NotificationFeed notifications={notifications} />
        </div>
      </div>
    </div>
  );
}

export default App;
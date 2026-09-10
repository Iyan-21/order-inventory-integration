import { useState } from 'react';
import './App.css';

const PRODUCTS = [
  { id: 'P100', label: 'Wireless Mouse (P100)' },
  { id: 'P200', label: 'Mechanical Keyboard (P200)' },
  { id: 'P300', label: 'USB-C Hub (P300)' },
];

function App() {
  const [productId, setProductId] = useState(PRODUCTS[0].id);
  const [quantity, setQuantity] = useState(1);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const res = await fetch('http://localhost:8080/api/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId, quantity: Number(quantity) }),
      });

      const data = await res.json();
      setResult(data);
    } catch (err) {
      setError('Could not reach the server. Is the backend running on :8080?');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 420, margin: '60px auto', fontFamily: 'sans-serif' }}>
      <h2>Place an Order</h2>

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: 12 }}>
          <label>Product</label><br />
          <select value={productId} onChange={(e) => setProductId(e.target.value)}>
            {PRODUCTS.map((p) => (
              <option key={p.id} value={p.id}>{p.label}</option>
            ))}
          </select>
        </div>

        <div style={{ marginBottom: 12 }}>
          <label>Quantity</label><br />
          <input
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </div>

        <button type="submit" disabled={loading}>
          {loading ? 'Placing order...' : 'Submit Order'}
        </button>
      </form>

      {error && (
        <p style={{ color: 'red', marginTop: 20 }}>{error}</p>
      )}

      {result && (
        <div style={{ marginTop: 20, padding: 16, border: '1px solid #ccc', borderRadius: 8 }}>
          <h3 style={{ color: result.status === 'CONFIRMED' ? 'green' : 'red' }}>
            {result.status}
          </h3>
          {result.reason && <p><strong>Reason:</strong> {result.reason}</p>}
          {result.inventory && (
            <p>
              <strong>{result.inventory.name}</strong> — stock remaining: {result.inventory.stock}
            </p>
          )}
        </div>
      )}
    </div>
  );
}

export default App;
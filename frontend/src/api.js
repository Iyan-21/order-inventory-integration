const BASE_URL = 'http://localhost:8080/api';

async function request(path, options = {}) {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });

  const contentType = res.headers.get('content-type') || '';
  const body = contentType.includes('application/json')
    ? await res.json().catch(() => null)
    : await res.text().catch(() => '');

  if (!res.ok) {
    const message = typeof body === 'string' ? body : body?.message || `Request failed (${res.status})`;
    throw new Error(message);
  }

  return body;
}

export function getInventory() {
  return request('/inventory');
}

export function getOrders() {
  return request('/orders');
}

export function getNotifications() {
  return request('/notifications');
}

export function placeOrder(items) {
  return request('/orders', {
    method: 'POST',
    body: JSON.stringify({ items }),
  });
}

export function cancelOrder(orderId) {
  return request(`/orders/${orderId}/cancel`, {
    method: 'POST',
  });
}
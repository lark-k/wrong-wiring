const API_URL = '/api/simulate'

export async function simulateWiring(payload) {
  const response = await fetch(API_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  })

  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || '仿真请求失败')
  }

  return response.json()
}

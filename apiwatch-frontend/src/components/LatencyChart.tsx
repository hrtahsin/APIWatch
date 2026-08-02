import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { useState } from 'react'
import type { HealthCheck } from '../types'

export function LatencyChart({ checks }: { checks: HealthCheck[] }) {
  const [range, setRange] = useState<'10' | 'all'>('10')
  const allData = [...checks]
    .reverse()
    .filter((check) => check.responseTimeMs !== null)
    .map((check) => ({
      time: new Intl.DateTimeFormat('en', {
        hour: 'numeric',
        minute: '2-digit',
      }).format(new Date(check.checkedAt)),
      timestamp: new Intl.DateTimeFormat('en', {
        dateStyle: 'medium',
        timeStyle: 'short',
      }).format(new Date(check.checkedAt)),
      latency: check.responseTimeMs,
    }))
  const data = range === '10' ? allData.slice(-10) : allData

  if (data.length === 0) {
    return <div className="chart-empty">Latency data appears after the first health check.</div>
  }

  return (
    <figure className="latency-figure" aria-label="Service latency trend">
      <figcaption className="sr-only">Latency in milliseconds over recent health checks.</figcaption>
      <div className="chart-toolbar">
        <span className="chart-legend"><i />Latency (ms)</span>
        {allData.length > 10 && (
          <div className="segmented-control compact-segments" aria-label="Latency chart range">
            <button
              className={range === '10' ? 'active' : ''}
              onClick={() => setRange('10')}
              type="button"
              aria-pressed={range === '10'}
            >
              Last 10
            </button>
            <button
              className={range === 'all' ? 'active' : ''}
              onClick={() => setRange('all')}
              type="button"
              aria-pressed={range === 'all'}
            >
              All
            </button>
          </div>
        )}
      </div>
      <div className="chart-container">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 8, right: 8, left: -18, bottom: 0 }}>
            <defs>
              <linearGradient id="latencyFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#5b6ff7" stopOpacity={0.22} />
                <stop offset="95%" stopColor="#5b6ff7" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="#e9edf5" strokeDasharray="4 4" vertical={false} />
            <XAxis
              dataKey="time"
              tick={{ fill: '#8992a5', fontSize: 11 }}
              axisLine={false}
              tickLine={false}
            />
            <YAxis
              tick={{ fill: '#8992a5', fontSize: 11 }}
              axisLine={false}
              tickLine={false}
              unit="ms"
            />
            <Tooltip
              contentStyle={{
                border: '1px solid #e1e6f0',
                borderRadius: 10,
                boxShadow: '0 10px 30px rgba(30, 42, 70, .12)',
              }}
              formatter={(value) => [`${value} ms`, 'Latency']}
              labelFormatter={(_, payload) => payload[0]?.payload.timestamp ?? ''}
            />
            <Area
              type="monotone"
              dataKey="latency"
              stroke="#5b6ff7"
              strokeWidth={2.2}
              fill="url(#latencyFill)"
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </figure>
  )
}

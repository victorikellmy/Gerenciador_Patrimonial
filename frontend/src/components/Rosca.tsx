import { Doughnut } from 'react-chartjs-2'
import { Chart as ChartJS, ArcElement, Tooltip } from 'chart.js'
import { numero } from '../utils/format'

ChartJS.register(ArcElement, Tooltip)

export interface FatiaRosca { chave: string; rotulo: string; quantidade: number; cor: string }

/**
 * Gráfico de rosca com leitura direta: total no centro, legenda em tabela com
 * quantidade e percentual, fatias zeradas escondidas e cores com significado.
 */
export default function Rosca({ fatias, unidade = 'bens' }: { fatias: FatiaRosca[]; unidade?: string }) {
  const visiveis = fatias.filter(f => f.quantidade > 0)
  const total = visiveis.reduce((s, f) => s + f.quantidade, 0)
  if (!total) return <div className="text-muted small py-4 text-center">Sem dados.</div>
  const bg = getComputedStyle(document.documentElement).getPropertyValue('--bg-surface').trim() || '#fff'
  return (
    <div className="rosca">
      <div className="rosca-grafico">
        <Doughnut
          data={{ labels: visiveis.map(f => f.rotulo), datasets: [{ data: visiveis.map(f => f.quantidade), backgroundColor: visiveis.map(f => f.cor), borderColor: bg, borderWidth: 2, hoverOffset: 6 }] }}
          options={{
            cutout: '68%', responsive: true, maintainAspectRatio: false,
            plugins: { legend: { display: false }, tooltip: { callbacks: { label: ctx => ` ${numero(ctx.parsed)} ${unidade} (${(100 * ctx.parsed / total).toFixed(1)}%)` } } },
          }}
        />
        <div className="rosca-centro"><div className="rosca-total">{numero(total)}</div><div className="rosca-unidade">{unidade}</div></div>
      </div>
      <table className="rosca-legenda">
        <tbody>
          {visiveis.map(f => (
            <tr key={f.chave}>
              <td><span className="rosca-cor" style={{ background: f.cor }}></span>{f.rotulo}</td>
              <td className="text-end rosca-qtd">{numero(f.quantidade)}</td>
              <td className="text-end rosca-pct">{(100 * f.quantidade / total).toFixed(1)}%</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

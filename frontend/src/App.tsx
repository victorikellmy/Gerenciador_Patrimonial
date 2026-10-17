import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth, destinoInicial } from './auth/AuthContext'
import type { Perfil } from './api/types'
import AppShell from './layout/AppShell'
import { Carregando } from './components/ui'
import Login from './pages/Login'
import TrocarSenha from './pages/TrocarSenha'
import Dashboard from './pages/Dashboard'
import PatrimoniosLista from './pages/patrimonios/Lista'
import PatrimonioForm from './pages/patrimonios/Form'
import PatrimonioDetalhe from './pages/patrimonios/Detalhe'
import Lotacoes from './pages/Lotacoes'
import Responsaveis from './pages/Responsaveis'
import LevantamentosLista from './pages/levantamentos/Lista'
import LevantamentoGuia from './pages/levantamentos/Guia'
import LevantamentoChecklist from './pages/levantamentos/Checklist'
import PendenciasLista from './pages/pendencias/Lista'
import PendenciaDetalhe from './pages/pendencias/Detalhe'
import FrotaGestao from './pages/frota/Gestao'
import FrotaVeiculos from './pages/frota/Veiculos'
import FrotaVeiculo from './pages/frota/Veiculo'
import VeiculoForm from './pages/frota/VeiculoForm'
import LancamentoForm from './pages/frota/LancamentoForm'
import FrotaListas from './pages/frota/Listas'
import Cautelas from './pages/frota/Cautelas'
import CautelaForm from './pages/frota/CautelaForm'
import DiariasLista from './pages/diarias/Lista'
import DiariaForm from './pages/diarias/Form'
import DiariaDetalhe from './pages/diarias/Detalhe'
import DiariasPagas from './pages/diarias/Pagas'
import Profissionais from './pages/diarias/Profissionais'
import ProfissionalForm from './pages/diarias/ProfissionalForm'
import Niveis from './pages/diarias/Niveis'
import Usuarios from './pages/Usuarios'
import UsuarioForm from './pages/UsuarioForm'
import Importacao from './pages/Importacao'
import Relatorios from './pages/Relatorios'
import Auditoria from './pages/Auditoria'

const ADM: Perfil[] = ['ADMINISTRADOR']
const OPER: Perfil[] = ['ADMINISTRADOR', 'FISCAL']
const LEITURA: Perfil[] = ['ADMINISTRADOR', 'FISCAL', 'GERENTE', 'PRESIDENTE', 'FINANCEIRO']
const FROTA: Perfil[] = ['ADMINISTRADOR', 'FISCAL', 'GERENTE', 'MOTORISTA']

function Protegida() {
  const { usuario, carregando } = useAuth()
  const location = useLocation()
  if (carregando) return <div className="vh-100 d-flex align-items-center justify-content-center"><Carregando /></div>
  if (!usuario) return <Navigate to="/login" replace state={{ de: location.pathname }} />
  if (usuario.senhaProvisoria && location.pathname !== '/minha-conta/senha') return <Navigate to="/minha-conta/senha?obrigatoria" replace />
  return <AppShell />
}

function Perfis({ perfis }: { perfis: Perfil[] }) {
  const { usuario, temPerfil } = useAuth()
  if (!temPerfil(...perfis)) return <Navigate to={destinoInicial(usuario?.perfil)} replace />
  return <Outlet />
}

/** Raiz: o dashboard para quem tem leitura; motorista (sem dashboard) vai para a frota. */
function Inicio() {
  const { usuario } = useAuth()
  return usuario?.perfil === 'MOTORISTA' ? <Navigate to="/frota" replace /> : <Dashboard />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<Protegida />}>
        <Route path="/" element={<Inicio />} />
        <Route path="/minha-conta/senha" element={<TrocarSenha />} />

        <Route element={<Perfis perfis={LEITURA} />}>
          <Route path="/patrimonios" element={<PatrimoniosLista />} />
          <Route path="/patrimonios/:id" element={<PatrimonioDetalhe />} />
          <Route path="/relatorios" element={<Relatorios />} />
          <Route path="/diarias" element={<DiariasLista />} />
          <Route path="/diarias/:id" element={<DiariaDetalhe />} />
          <Route path="/diarias/pagas" element={<DiariasPagas />} />
        </Route>

        <Route element={<Perfis perfis={OPER} />}>
          <Route path="/patrimonios/novo" element={<PatrimonioForm />} />
          <Route path="/patrimonios/:id/editar" element={<PatrimonioForm />} />
          <Route path="/lotacoes" element={<Lotacoes />} />
          <Route path="/responsaveis" element={<Responsaveis />} />
          <Route path="/levantamentos" element={<LevantamentosLista />} />
          <Route path="/levantamentos/:id" element={<LevantamentoGuia />} />
          <Route path="/levantamentos/:id/locais/:lotacaoId" element={<LevantamentoChecklist />} />
          <Route path="/pendencias" element={<PendenciasLista />} />
          <Route path="/pendencias/:id" element={<PendenciaDetalhe />} />
          <Route path="/pendencias/:pendenciaId/patrimoniar" element={<PatrimonioForm />} />
          <Route path="/diarias/nova" element={<DiariaForm />} />
          <Route path="/diarias/:id/editar" element={<DiariaForm />} />
          <Route path="/diarias/profissionais" element={<Profissionais />} />
          <Route path="/diarias/profissionais/novo" element={<ProfissionalForm />} />
          <Route path="/diarias/profissionais/:id/editar" element={<ProfissionalForm />} />
          <Route path="/diarias/niveis" element={<Niveis />} />
        </Route>

        <Route element={<Perfis perfis={FROTA} />}>
          <Route path="/frota" element={<FrotaVeiculos />} />
          <Route path="/frota/gestao" element={<Perfis perfis={['ADMINISTRADOR', 'FISCAL', 'GERENTE']} />}>
            <Route index element={<FrotaGestao />} />
          </Route>
          <Route path="/frota/veiculos/novo" element={<VeiculoForm />} />
          <Route path="/frota/:id" element={<FrotaVeiculo />} />
          <Route path="/frota/:id/editar" element={<VeiculoForm />} />
          <Route path="/frota/:id/abastecimentos/novo" element={<LancamentoForm tipo="abastecimento" />} />
          <Route path="/frota/:id/manutencoes/novo" element={<LancamentoForm tipo="manutencao" />} />
          <Route path="/frota/:id/deslocamentos/novo" element={<LancamentoForm tipo="deslocamento" />} />
          <Route path="/frota/abastecimentos/:lancamentoId/editar" element={<LancamentoForm tipo="abastecimento" />} />
          <Route path="/frota/manutencoes/:lancamentoId/editar" element={<LancamentoForm tipo="manutencao" />} />
          <Route path="/frota/deslocamentos/:lancamentoId/editar" element={<LancamentoForm tipo="deslocamento" />} />
          <Route path="/frota/abastecimentos" element={<FrotaListas tipo="abastecimentos" />} />
          <Route path="/frota/manutencoes" element={<FrotaListas tipo="manutencoes" />} />
          <Route path="/frota/deslocamentos" element={<FrotaListas tipo="deslocamentos" />} />
          <Route path="/frota/cautelas" element={<Cautelas />} />
          <Route path="/frota/cautelas/nova" element={<CautelaForm />} />
          <Route path="/frota/cautelas/:id/editar" element={<CautelaForm />} />
        </Route>

        <Route element={<Perfis perfis={ADM} />}>
          <Route path="/usuarios" element={<Usuarios />} />
          <Route path="/usuarios/novo" element={<UsuarioForm />} />
          <Route path="/usuarios/:id/editar" element={<UsuarioForm />} />
          <Route path="/importacao" element={<Importacao />} />
          <Route path="/auditoria" element={<Auditoria />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api'
import { Card, Carregando, EmptyState, Erro, PageHeader, Paginacao, useToast } from '../components/ui'
import { dataHora, rotulo } from '../utils/format'

export default function Usuarios() {
  const qc = useQueryClient()
  const toast = useToast()
  const [page, setPage] = useState(0)
  const q = useQuery({ queryKey: ['usuarios', page], queryFn: () => api.usuarios.listar(page) })
  const inativar = async (id: number, nome: string) => { if (!confirm(`Inativar o usuário ${nome}?`)) return; try { await api.usuarios.inativar(id); toast.sucesso('Usuário inativado.'); qc.invalidateQueries({ queryKey: ['usuarios'] }) } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } }
  return (
    <>
      <PageHeader titulo="Usuários" subtitulo="Contas de acesso ao sistema e seus perfis." acoes={<Link className="btn btn-primary" to="/usuarios/novo"><i className="bi bi-plus-lg"></i> Novo usuário</Link>} />
      <Card>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : q.data!.empty ? <EmptyState icon="bi-people" titulo="Nenhum usuário" /> : (
          <>
            <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
              <thead><tr><th>Nome</th><th>Login</th><th>Perfil</th><th>Situação</th><th>Senha</th><th>Criado em</th><th></th></tr></thead>
              <tbody>{q.data!.content.map(u => <tr key={u.id} className={u.ativo ? '' : 'text-muted'}>
                <td><strong>{u.nomeCompleto}</strong></td><td><code>{u.login}</code></td><td><span className="badge badge-plain text-bg-light">{rotulo(u.perfil)}</span></td>
                <td><span className={`badge badge-plain ${u.ativo ? 'text-bg-success' : 'text-bg-secondary'}`}>{u.ativo ? 'Ativo' : 'Inativo'}</span></td>
                <td>{u.senhaProvisoria ? <span className="badge badge-plain text-bg-warning" title="Deverá trocar a senha no próximo acesso">Provisória</span> : <span className="text-muted small">definida</span>}</td>
                <td className="small">{dataHora(u.criadoEm)}</td>
                <td className="text-end text-nowrap"><Link className="btn btn-sm btn-secondary btn-icon" to={`/usuarios/${u.id}/editar`} title="Editar"><i className="bi bi-pencil"></i></Link>{' '}{u.ativo && <button className="btn btn-sm btn-outline-danger btn-icon" title="Inativar" onClick={() => inativar(u.id, u.login)}><i className="bi bi-person-x"></i></button>}</td>
              </tr>)}</tbody>
            </table></div>
            <div className="px-3 pb-3"><Paginacao pagina={q.data!} onPage={setPage} /></div>
          </>)}
      </Card>
    </>
  )
}

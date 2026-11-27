import { notFoundMessage } from '../messages'

/** 「該当する〇〇がありません。」 name に〇〇（例: 入庫予定）を渡す。 */
export default function NotFound({ name }: { name: string }) {
  return <p className="text-slate-600">{notFoundMessage(name)}</p>
}

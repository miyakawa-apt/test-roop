/** 一覧右上の件数表示「N件」。 */
export default function CountLabel({ count }: { count: number }) {
  return <p className="text-right text-sm text-slate-600">{count}件</p>
}

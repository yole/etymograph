import {fetchBackend} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useRouter} from "next/router";
import WordLink from "@/components/WordLink";

export const config = {
    unstable_runtimeJS: true
}

export async function getServerSideProps(context) {
    const graph = context.params.graph
    const q = String(context.query.q || "")
    const lang = context.query.lang ? String(context.query.lang) : undefined
    const page = parseInt(String(context.query.page || '1'))
    const pageSize = parseInt(String(context.query.pageSize || '50'))
    const offset = (page - 1) * pageSize
    const params = new URLSearchParams()
    params.set('q', q)
    if (lang) params.set('lang', lang)
    params.set('limit', String(pageSize))
    params.set('offset', String(offset))
    // auto mode by default
    const url = `search?${params.toString()}`
    return fetchBackend(graph, url, true)
}

export default function SearchPage(params) {
    const data = params.loaderData
    const router = useRouter()
    const graph = router.query.graph
    const q = String(router.query.q || '')

    const matches = data.matches as Array<any>
    const usedMode = data.usedMode as string

    const total = usedMode === 'exact' ? data.totalExact : data.totalPrefix

    return <>
        <Breadcrumbs title={`Search results`}/>
        <h3>Search results for &apos;{q}&apos;</h3>
        {matches.length === 0 && <p>No results</p>}

        {matches.length > 0 && <>
            <p>{usedMode === 'exact' ? `${data.totalExact} match(es)` : `${data.totalPrefix} prefix match(es)`}</p>
            <ul>
                {matches.map((m) => (
                    <li key={`${m.language}-${m.id}`}>
                        <WordLink word={m} baseLanguage="?" gloss={true}/>
                    </li>
                ))}
            </ul>
        </>}

        {/* Basic pagination (no links wiring for brevity in Phase 1) */}
        {total > 50 && <p>Pagination: showing up to {matches.length} of {total}</p>}
    </>
}

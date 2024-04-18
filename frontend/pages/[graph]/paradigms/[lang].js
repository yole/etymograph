import {allowEdit, fetchAllLanguagePaths, fetchBackend} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";
import Breadcrumbs from "@/components/Breadcrumbs";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `paradigms/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function ParadigmList(params) {
    const paradigmList = params.loaderData
    const router = useRouter()
    const graph = router.query.graph

    return <>
        <Breadcrumbs langId={router.query.lang} langName={paradigmList.langFullName} title="Paradigms"/>
        <ul>
            {paradigmList.paradigms.map(p => <li key={p.id}><Link href={`/${graph}/paradigm/${p.id}`}>{p.name}</Link></li>)}
        </ul>
        {allowEdit() && <button onClick={() => router.push(`/${graph}/paradigms/${router.query.lang}/new`)}>Add paradigm</button>}
    </>
}

import {useEffect} from "react";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";

export async function getStaticProps(context) {
    return fetchBackend(`paradigms/${context.params.lang}`)
}

export async function getStaticPaths() {
    return await fetchAllLanguagePaths();
}

export default function ParadigmList(params) {
    const paradigmList = params.loaderData
    const router = useRouter()

    useEffect(() => { document.title = `Etymograph : ${paradigmList.langFullName} : Paradigms` })

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
                {<Link href={`/language/${router.query.lang}`}>{paradigmList.langFullName}</Link>} {'> '}
            </small>
            Paradigms
        </h2>
        <ul>
            {paradigmList.paradigms.map(p => <li key={p.id}><Link href={`/paradigm/${p.id}`}>{p.name}</Link></li>)}
        </ul>
        <Link href={`/paradigms/${router.query.lang}/new`}>Add paradigm</Link>
    </>
}

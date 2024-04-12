import {useRouter} from "next/router";
import CorpusTextForm from "@/components/CorpusTextForm";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import Link from "next/link";

export async function getStaticProps(context) {
    return fetchBackend(`language/${context.params.lang}`)
}

export async function getStaticPaths() {
    return await fetchAllLanguagePaths();
}

export default function CorpusTextEditor(params) {
    const langData = params.loaderData
    const router = useRouter()

    const lang = router.query.lang

    function submitted(r) {
        router.push("/corpus/text/" + r.id)
    }

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
                <Link href={`/language/${lang}`}>{langData.name}</Link> {'> '}
            </small>
            New Corpus Text
        </h2>

        <CorpusTextForm lang={lang} submitted={submitted}/>
    </>
}

import {useRouter} from "next/router";
import PhonemeForm from "@/components/PhonemeForm";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `language/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function PhonemeEditor() {
    const router = useRouter()
    const lang = router.query.lang

    return <>
        <Breadcrumbs langId={lang} title="New Phoneme"/>
        <PhonemeForm language={lang} redirectOnCreate={(r) => `/${router.query.graph}/phoneme/${r.id}`}/>
    </>
}

import {useRouter} from "next/router";
import PhonemeForm from "@/components/PhonemeForm";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `language/${context.params.lang}`)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function PhonemeEditor(params) {
    const langData = params.loaderData
    const router = useRouter()
    const lang = router.query.lang

    function submitted(id) {
        router.push(`/${router.query.graph}/phoneme/${id}`)
    }

    return <>
        <Breadcrumbs langId={lang} langName={langData.name} title="New Phoneme"/>
        <PhonemeForm language={lang} submitted={submitted}/>
    </>
}

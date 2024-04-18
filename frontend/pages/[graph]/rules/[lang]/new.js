import RuleForm from "@/forms/RuleForm";
import {useRouter} from "next/router";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `language/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function RuleEditor(params) {
    const langData = params.loaderData
    const router = useRouter()
    const lang = router.query.lang

    return <>
        <Breadcrumbs langId={lang} langName={langData.name} title="New Rule"/>
        <RuleForm redirectOnCreate={(r) => `/${router.query.graph}/rule/${r.id}`}
                  defaultValues={{fromLang: lang, toLang: lang}}/>
    </>
}

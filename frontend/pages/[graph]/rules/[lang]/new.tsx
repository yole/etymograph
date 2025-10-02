import RuleForm from "@/forms/RuleForm";
import {useRouter} from "next/router";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useContext} from "react";
import {GlobalStateContext} from "@/components/Contexts";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `language/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function RuleEditor(params) {
    const langData = params.loaderData
    const router = useRouter()
    const lang = router.query.lang as string
    const globalState = useContext(GlobalStateContext)
    console.log(globalState)
    const protoLang =
        (globalState !== undefined && lang !== undefined)
            ? globalState.languages.find((g) => g.shortName === lang)?.protoLanguageShortName
            : lang

    return <>
        <Breadcrumbs langId={lang} langName={langData.name} title="New Rule"/>
        <RuleForm redirectOnCreate={(r) => `/${router.query.graph}/rule/${r.id}`}
                  defaultValues={{name: '', text: '', fromLang: protoLang, toLang: lang}}/>
    </>
}

import {useRouter} from "next/router";
import {RuleListView} from "@/pages/[graph]/rules/[lang]";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import {RuleListViewModel} from "@/models";
import Breadcrumbs from "@/components/Breadcrumbs";
import LanguageNavBar from "@/components/LanguageNavBar";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `rules/${context.params.lang}/morpho`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function MorphoRuleList(params) {
    const ruleList = params.loaderData as RuleListViewModel
    const router = useRouter()
    const lang = router.query.lang as string
    return <>
        <Breadcrumbs langId={lang} langName={ruleList.toLangFullName} title="Morphology"/>
        <LanguageNavBar langId={lang}/>
        <p/>
        <RuleListView list={ruleList}/>
    </>
}

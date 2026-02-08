import {useRouter} from "next/router";
import {RuleListView} from "@/pages/[graph]/rules/[lang]";
import {allowEdit, fetchAllLanguagePaths, fetchBackend, generateParadigm} from "@/api";
import {RuleListViewModel} from "@/models";
import Breadcrumbs from "@/components/Breadcrumbs";
import LanguageNavBar from "@/components/LanguageNavBar";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `rules/${context.params.lang}/phono`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function MorphoRuleList(params) {
    const ruleList = params.loaderData as RuleListViewModel
    const router = useRouter()
    const lang = router.query.lang as string
    const graph = router.query.graph as string
    return <>
        <Breadcrumbs langId={lang} langName={ruleList.toLangFullName} title="Historical Phonology"/>
        <LanguageNavBar langId={lang}/>
        <p/>
        <RuleListView list={ruleList}/>
        {allowEdit() && <p>
            <button className="uiButton" onClick={() => router.push(`/${graph}/rules/${lang}/new?type=phono`)}>Add Rule</button>
            {' '}
            <button className="uiButton" onClick={() => router.push(`/${graph}/rules/sequence/new`)}>Add Rule Sequence</button>
        </p>}
    </>
}

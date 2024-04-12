import RuleForm from "@/forms/RuleForm";
import {useRouter} from "next/router";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";

export async function getStaticProps(context) {
    return fetchBackend(`language/${context.params.lang}`)
}

export async function getStaticPaths() {
    return await fetchAllLanguagePaths();
}

export default function RuleEditor(params) {
    const langData = params.loaderData
    const router = useRouter()
    const lang = router.query.lang

    return <>
        <Breadcrumbs langId={lang} langName={langData.name} title="New Rule"/>
        <RuleForm redirectOnCreate={(r) => `/rule/${r.id}`}
                  defaultValues={{fromLang: lang, toLang: lang}}/>
    </>
}

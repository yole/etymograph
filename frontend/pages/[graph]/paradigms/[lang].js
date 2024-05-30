import {allowEdit, fetchAllLanguagePaths, fetchBackend, generateParadigm} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";
import Breadcrumbs from "@/components/Breadcrumbs";
import EtymographFormView from "@/components/EtymographFormView";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";

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
    const lang = router.query.lang

    return <>
        <Breadcrumbs langId={lang} langName={paradigmList.langFullName} title="Paradigms"/>
        <ul>
            {paradigmList.paradigms.map(p => <li key={p.id}><Link href={`/${graph}/paradigm/${p.id}`}>{p.name}</Link></li>)}
        </ul>
        {allowEdit() && <>
            <button onClick={() => router.push(`/${graph}/paradigms/${lang}/new`)}>Add paradigm</button>{' '}

            <EtymographFormView editButtonTitle="Generate">
                <EtymographForm
                      create={(data) => generateParadigm(graph, lang, data)}
                      redirectOnCreate={(r) => `/${graph}/paradigm/${r.id}`}>

                    <table><tbody>
                        <FormRow label="Name" id="name"/>
                        <FormRow label="POS" id="pos"/>
                        <FormRow label="Prefix" id="prefix"/>
                        <FormRow label="Rows" id="rows"/>
                        <FormRow label="Columns" id="columns"/>
                    </tbody></table>
                </EtymographForm>
            </EtymographFormView>
        </>}
    </>
}

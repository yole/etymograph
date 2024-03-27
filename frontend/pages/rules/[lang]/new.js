import RuleForm from "@/components/RuleForm";
import {useRouter} from "next/router";
import Link from "next/link";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";

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

    function submitted(id) {
        router.push("/rule/" + id)
    }

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
                <Link href={`/language/${lang}`}>{langData.name}</Link> {'> '}
            </small>
            New Rule
        </h2>
        <RuleForm submitted={submitted} initialFromLanguage={lang} initialToLanguage={lang}/>
    </>
}

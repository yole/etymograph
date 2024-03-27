import {useRouter} from "next/router";
import PhonemeForm from "@/components/PhonemeForm";
import Link from "next/link";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";

export async function getStaticProps(context) {
    return fetchBackend(`language/${context.params.lang}`)
}

export async function getStaticPaths() {
    return await fetchAllLanguagePaths();
}

export default function PublicationEditor(params) {
    const langData = params.loaderData
    const router = useRouter()
    const lang = router.query.lang

    function submitted(id) {
        router.push("/phoneme/" + id)
    }

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
                <Link href={`/language/${lang}`}>{langData.name}</Link> {'> '}
            </small>
            New Phoneme
        </h2>
        <PhonemeForm language={lang} submitted={submitted}/>
    </>
}

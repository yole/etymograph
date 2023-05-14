import {allowEdit, fetchBackend} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context){
    return fetchBackend(`word/${context.params.id}/paradigms`, { headers: { 'Accept': 'application/json'} })
}

export async function getStaticPaths() {
    const {props} = await fetchBackend(`language`)
    const paths = []
    for (const lang of props.loaderData) {
        let url = `dictionary/${lang.shortName}/all`
        const dictData = await fetchBackend(url)
        for (const word of dictData.props.loaderData.words) {
            paths.push({params: {lang: lang.shortName, id: word.id.toString()}})
        }
    }
    return {paths, fallback: allowEdit()}
}

export default function WordParadigms(params) {
    const paradigmList = params.loaderData
    const router = useRouter()
    return <>
        <h2><small>
            <Link href={`/`}>Etymograph</Link> {'> '}
            <Link href={`/language/${paradigmList.language}`}>{paradigmList.languageFullName}</Link> {'> '}
            <Link href={`/dictionary/${paradigmList.language}`}>Dictionary</Link> {'> '}
            <Link href={`/word/${paradigmList.language}/${paradigmList.word}`}>{paradigmList.word}</Link></small> {'>'} Paradigms</h2>
        {paradigmList.paradigms.map(p => <>
            <h3>{p.name}</h3>
            <table>
                <thead>
                <tr>
                    <td/>
                    {p.columnTitles.map(t => <td>{t}</td>)}
                </tr>
                </thead>
                <tbody>
                {p.rowTitles.map((t, i) => <tr>
                    <td>{t}</td>
                    {p.cells.map(columnWords => <td>
                        {columnWords[i]?.map((alt, ai) => <>
                            {ai > 0 && <>&nbsp;|&nbsp;</>}
                            {alt?.wordId > 0 ? <Link href={`/word/${router.query.lang}/${alt?.word}`}>{alt?.word}</Link>: alt?.word}
                        </>)}
                    </td>)}
                </tr>)}
                </tbody>
            </table>
        </>)}
    </>
}

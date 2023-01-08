import {useLoaderData, useParams} from "react-router";
import {Link} from "react-router-dom";

export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}word/${params.id}/paradigms`, { headers: { 'Accept': 'application/json'} })
}

export default function WordParadigms() {
    const paradigms = useLoaderData()
    const params = useParams()
    return paradigms.map(p => <>
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
                        {alt?.wordId > 0 ? <Link to={`/word/${params.lang}/${alt?.word}`}>{alt?.word}</Link>: alt?.word}
                    </>)}
                </td>)}
            </tr>)}
            </tbody>
        </table>
    </>)
}

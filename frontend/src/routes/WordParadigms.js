import {useLoaderData} from "react-router";

export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}word/${params.id}/paradigms`, { headers: { 'Accept': 'application/json'} })
}

export default function WordParadigms() {
    const paradigms = useLoaderData()
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
                {p.cells.map(c => <td>{c[i]}</td>)}
            </tr>)}
            </tbody>
        </table>
    </>)
}

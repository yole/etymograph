import {useLoaderData} from "react-router";
import {Link} from "react-router-dom";
import {useEffect} from "react";

export async function loader() {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}rules`, { headers: { 'Accept': 'application/json'} })
}

export default function RuleList() {
    const rules = useLoaderData()
    useEffect(() => { document.title = "Etymograph : Rules" })

    return <>
    <h2>Rules</h2>
        <ul>
            {rules.map(r => <li key={r.id}><Link to={`/rule/${r.id}`}>{r.name}</Link>{r.summaryText.length > 0 ? ": " + r.summaryText : ""}</li>)}
        </ul>
        <Link to="/rules/new">Add rule</Link>
    </>
}

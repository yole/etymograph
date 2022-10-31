import {useLoaderData} from "react-router";
import {Link} from "react-router-dom";

export async function loader() {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}rules`, { headers: { 'Accept': 'application/json'} })
}

export default function RuleList() {
    const rules = useLoaderData()
    return <>
    <h2>Rules</h2>
        <ul>
            {rules.map(r => <li key={r.id}><Link to={`/rule/${r.id}`}>{r.addedCategories}</Link></li>)}
        </ul>
        <Link to="/rules/new">Add rule</Link>
    </>
}

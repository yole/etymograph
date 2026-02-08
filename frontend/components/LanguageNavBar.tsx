import Link from "next/link";
import {useRouter} from "next/router";

export default function LanguageNavBar(props: {langId: string}) {
    const langId = props.langId
    const router = useRouter()
    const graph = router.query.graph as string;
    return <>
        <Link href={`/${graph}/language/${langId}`}>Language</Link>
        {' '}| <Link href={`/${graph}/dictionary/${langId}`}>Dictionary</Link>
        {' '}| <Link href={`/${graph}/dictionary/${langId}/compounds`}>Compounds</Link>
        {' '}| <Link href={`/${graph}/dictionary/${langId}/names`}>Names</Link>
        {' '}| <Link href={`/${graph}/dictionary/${langId}/reconstructed`}>Reconstructed words</Link>
        {' '}| <Link href={`/${graph}/rules/${langId}/morpho`}>Morphology</Link>
        {' '}| <Link href={`/${graph}/rules/${langId}/phono`}>Historical Phonology</Link>
        {' '}| <Link href={`/${graph}/corpus/${langId}`}>Corpus</Link>
    </>
}
